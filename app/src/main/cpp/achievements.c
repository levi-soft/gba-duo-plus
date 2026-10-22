#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include <time.h>
#include <mgba/core/core.h>
#include "rc_client.h"
#include "rc_consoles.h"
#include "rc_hash.h"

extern struct mCore* gbaduo_active_core(void);
extern const uint8_t* gbaduo_rom_data(size_t* size);

/* Every entry point and server response runs on the emulation thread. HTTP is
 * performed by Kotlin workers; no network worker ever reads emulator memory. */
static rc_client_t* client;
static uint32_t console_id;
static char game_hash[33];
static char original_hash[33];
static char message[512];
static int closing;
static uint64_t next_request;
typedef struct Request {
    uint64_t id;
    char *url, *post, *type;
    rc_client_server_callback_t callback;
    void* data;
    int dispatched;
    struct Request* next;
} Request;
static Request* requests;
typedef struct Event { char* json; struct Event* next; } Event;
static Event *events, *events_tail;

typedef struct Json { char* text; size_t length, capacity; } Json;
static void append(Json* j, const char* format, ...) {
    va_list a, copy; va_start(a, format); va_copy(copy, a);
    int n = vsnprintf(NULL, 0, format, copy); va_end(copy);
    if (n < 0) { va_end(a); return; }
    size_t need = j->length + (size_t)n + 1;
    if (need > j->capacity) { j->capacity = need * 2; j->text = realloc(j->text, j->capacity); }
    if (!j->text) abort();
    vsnprintf(j->text + j->length, j->capacity - j->length, format, a);
    va_end(a); j->length += (size_t)n;
}
static void string(Json* j, const char* value) {
    append(j, "\"");
    for (const unsigned char* p=(const unsigned char*)(value ? value : ""); *p; p++) {
        if (*p=='"' || *p=='\\') append(j, "\\%c", *p);
        else if (*p<32) append(j, "\\u%04x", *p);
        else append(j, "%c", *p);
    }
    append(j, "\"");
}
static jbyteArray bytes(JNIEnv* env, const void* data, size_t size) {
    jbyteArray result = (*env)->NewByteArray(env, (jsize)size);
    if (result && size) (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, data);
    return result;
}
static jbyteArray json_result(JNIEnv* env, Json* j) {
    jbyteArray result=bytes(env, j->text, j->length); free(j->text); return result;
}
static void event_json(Json* j) {
    if (closing) { free(j->text); return; }
    Event* e=calloc(1,sizeof(*e)); e->json=j->text;
    if(events_tail)events_tail->next=e;else events=e;events_tail=e;
}
static void notice(const char* text) {
    Json j={0}; append(&j,"{\"type\":\"notice\",\"text\":");string(&j,text);append(&j,"}");event_json(&j);
}
static rc_clock_t now_ms(const rc_client_t* c) {
    (void)c;struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (rc_clock_t)t.tv_sec*1000+t.tv_nsec/1000000;
}
static uint32_t read_memory(uint32_t address,uint8_t* buffer,uint32_t size,rc_client_t* c) {
    (void)c;struct mCore* core=gbaduo_active_core();if(!core)return 0;
    uint32_t read=0;
    while(read<size){
        uint32_t a=address+read,physical=a;int bank=-1;
        if(a<address)break;
        if(console_id==RC_CONSOLE_GAMEBOY_ADVANCE){
            if(a<0x8000)physical=0x03000000+a;
            else if(a<0x48000)physical=0x02000000+a-0x8000;
            else if(a<0x58000)physical=0x0e000000+a-0x48000;
            else break;
        }else if(console_id==RC_CONSOLE_GAMEBOY_COLOR){
            if(a>=0x10000&&a<0x16000){physical=0xd000+(a-0x10000)%0x1000;bank=2+(a-0x10000)/0x1000;}
            else if(a>=0x16000&&a<0x34000){physical=0xa000+(a-0x16000)%0x2000;bank=1+(a-0x16000)/0x2000;}
            else if(a>=0x10000)break;
        }else if(a>=0x10000)break;
        buffer[read++]=(uint8_t)core->rawRead8(core,physical,bank);
    }
    return read;
}
static void server_call(const rc_api_request_t* request,rc_client_server_callback_t callback,void* data,rc_client_t* c){
    (void)c;
    if(closing){rc_api_server_response_t r={"",0,RC_API_SERVER_RESPONSE_CLIENT_ERROR};callback(&r,data);return;}
    Request* r=calloc(1,sizeof(*r));r->id=++next_request;r->url=strdup(request->url);
    r->post=request->post_data?strdup(request->post_data):NULL;r->type=strdup(request->content_type?request->content_type:"application/x-www-form-urlencoded");
    r->callback=callback;r->data=data;r->next=requests;requests=r;
}
static void loaded(int result,const char* error,rc_client_t* c,void* data){
    (void)data;
    if(result!=RC_OK){
        if(result==RC_NO_GAME_LOADED){
            snprintf(message,sizeof(message),"No achievements found for this ROM.");
            Json j={0};append(&j,"{\"type\":\"popup\",\"title\":\"No achievements found for this ROM\",\"subtitle\":\"You can keep playing normally.\"}");event_json(&j);
        }else{snprintf(message,sizeof(message),"%s",error?error:rc_error_str(result));notice(message);}
        return;
    }
    const rc_client_game_t* game=rc_client_get_game_info(c);
    if(!game||!game->id){
        snprintf(message,sizeof(message),"No achievements found for this ROM.");
        Json j={0};append(&j,"{\"type\":\"popup\",\"title\":\"No achievements found for this ROM\",\"subtitle\":\"You can keep playing normally.\"}");event_json(&j);return;
    }
    snprintf(message,sizeof(message),"%s",game->title);
    rc_client_user_game_summary_t summary={0};rc_client_get_user_game_summary(c,&summary);
    Json j={0};append(&j,"{\"type\":\"popup\",\"title\":");string(&j,game->title);append(&j,",\"badge\":");string(&j,game->badge_url);
    append(&j,",\"subtitle\":\"%u/%u unlocked\"}",summary.num_unlocked_achievements,summary.num_core_achievements);event_json(&j);
}
static void logged_in(int result,const char* error,rc_client_t* c,void* data){
    (void)data;
    if(result!=RC_OK){snprintf(message,sizeof(message),"%s",error?error:rc_error_str(result));notice(message);return;}
    const rc_client_user_t* user=rc_client_get_user_info(c);
    if(user){Json j={0};append(&j,"{\"type\":\"auth\",\"username\":");string(&j,user->username);append(&j,",\"token\":");string(&j,user->token);append(&j,"}");event_json(&j);}
    snprintf(message,sizeof(message),"Loading achievements...");
    rc_client_begin_load_game(c,game_hash,loaded,NULL);
}
static void handle_event(const rc_client_event_t* event,rc_client_t* c){
    (void)c;
    if(event->type==RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED&&event->achievement){
        const rc_client_achievement_t* a=event->achievement;
        Json j={0};append(&j,"{\"type\":\"unlock\",\"title\":");string(&j,a->title);
        append(&j,",\"badge\":");string(&j,a->badge_url);
        append(&j,",\"points\":%u}",a->points);event_json(&j);
    }else if(event->type==RC_CLIENT_EVENT_SERVER_ERROR&&event->server_error){
        notice("RetroAchievements could not confirm a server request. Check the connection.");
    }else if(event->type==RC_CLIENT_EVENT_DISCONNECTED)notice("RetroAchievements offline: unlocks pending.");
    else if(event->type==RC_CLIENT_EVENT_RECONNECTED)notice("RetroAchievements reconnected: pending unlocks submitted.");
    else if(event->type==RC_CLIENT_EVENT_GAME_COMPLETED)notice("All achievements unlocked!");
}
static void free_request(Request* r){free(r->url);free(r->post);free(r->type);free(r);}
static void shutdown_client(void){
    closing=1;
    if(client){
        rc_client_logout(client);
        while(requests){Request* r=requests;requests=r->next;rc_api_server_response_t response={"",0,RC_API_SERVER_RESPONSE_CLIENT_ERROR};r->callback(&response,r->data);free_request(r);}
        rc_client_destroy(client);client=NULL;
    }
    while(events){Event* e=events;events=e->next;free(e->json);free(e);}events_tail=NULL;
    closing=0;
}

#define JNI_METHOD(name) Java_com_khumam_gbaduo_achievements_NativeAchievements_##name
JNIEXPORT void JNICALL JNI_METHOD(start)(JNIEnv* env,jobject self){
    (void)env;(void)self;shutdown_client();game_hash[0]=0;original_hash[0]=0;
    struct mCore* core=gbaduo_active_core();size_t size=0;const uint8_t* rom=gbaduo_rom_data(&size);
    if(!core||!rom)return;
    console_id=core->platform(core)==mPLATFORM_GBA?RC_CONSOLE_GAMEBOY_ADVANCE:(size>0x143&&(rom[0x143]&0x80)?RC_CONSOLE_GAMEBOY_COLOR:RC_CONSOLE_GAMEBOY);
    rc_hash_iterator_t iterator;rc_hash_initialize_iterator(&iterator,"game.gba",rom,size);
    game_hash[0]=0; original_hash[0]=0;
    int ok=rc_hash_generate(game_hash,console_id,&iterator);rc_hash_destroy_iterator(&iterator);
    if(ok)memcpy(original_hash,game_hash,sizeof(original_hash));
    client=rc_client_create(read_memory,server_call);
    rc_client_set_host(client,"https://retroachievements.org");
    rc_client_set_hardcore_enabled(client,0);
    rc_client_set_event_handler(client,handle_event);
    rc_client_set_get_time_millisecs_function(client,now_ms);
    rc_client_set_allow_background_memory_reads(client,0);
    snprintf(message,sizeof(message),"%s",ok?"Sign in to RetroAchievements":"Could not identify this ROM.");
}
JNIEXPORT void JNICALL JNI_METHOD(login)(JNIEnv* env,jobject self,jstring username,jstring secret,jboolean token){
    (void)self;if(!client)return;
    const char* u=(*env)->GetStringUTFChars(env,username,NULL);const char* s=(*env)->GetStringUTFChars(env,secret,NULL);
    snprintf(message,sizeof(message),"Signing in...");
    if(token)rc_client_begin_login_with_token(client,u,s,logged_in,NULL);else rc_client_begin_login_with_password(client,u,s,logged_in,NULL);
    (*env)->ReleaseStringUTFChars(env,username,u);(*env)->ReleaseStringUTFChars(env,secret,s);
}
JNIEXPORT void JNICALL JNI_METHOD(logout)(JNIEnv* env,jobject self){(void)env;(void)self;if(client)rc_client_logout(client);snprintf(message,sizeof(message),"Signed out");}
JNIEXPORT void JNICALL JNI_METHOD(frame)(JNIEnv* env,jobject self,jboolean paused){(void)env;(void)self;if(client){if(paused)rc_client_idle(client);else rc_client_do_frame(client);}}
JNIEXPORT void JNICALL JNI_METHOD(stop)(JNIEnv* env,jobject self){(void)env;(void)self;shutdown_client();}
JNIEXPORT jbyteArray JNICALL JNI_METHOD(pollRequest)(JNIEnv* env,jobject self){
    (void)self;for(Request* r=requests;r;r=r->next)if(!r->dispatched){
        r->dispatched=1;Json j={0};append(&j,"{\"id\":%llu,\"url\":",(unsigned long long)r->id);string(&j,r->url);append(&j,",\"post\":");if(r->post)string(&j,r->post);else append(&j,"null");append(&j,",\"contentType\":");string(&j,r->type);append(&j,"}");return json_result(env,&j);
    }return NULL;
}
JNIEXPORT void JNICALL JNI_METHOD(response)(JNIEnv* env,jobject self,jlong id,jbyteArray body,jint status){
    (void)self;Request** p=&requests;while(*p&&(*p)->id!=(uint64_t)id)p=&(*p)->next;if(!*p)return;
    Request* r=*p;*p=r->next;jsize size=(*env)->GetArrayLength(env,body);char* text=malloc((size_t)size+1);
    (*env)->GetByteArrayRegion(env,body,0,size,(jbyte*)text);text[size]=0;rc_api_server_response_t response={text,(size_t)size,status};r->callback(&response,r->data);free(text);free_request(r);
}
JNIEXPORT jbyteArray JNICALL JNI_METHOD(pollEvent)(JNIEnv* env,jobject self){
    (void)self;if(!events)return NULL;Event* e=events;events=e->next;if(!events)events_tail=NULL;jbyteArray result=bytes(env,e->json,strlen(e->json));free(e->json);free(e);return result;
}
JNIEXPORT jbyteArray JNICALL JNI_METHOD(status)(JNIEnv* env,jobject self){
    (void)self;Json j={0};const rc_client_user_t* user=client?rc_client_get_user_info(client):NULL;const rc_client_game_t* game=client?rc_client_get_game_info(client):NULL;
    rc_client_user_game_summary_t summary={0};if(client)rc_client_get_user_game_summary(client,&summary);
    append(&j,"{\"originalHash\":");string(&j,original_hash);append(&j,",\"activeHash\":");string(&j,game_hash);append(&j,",");
    append(&j,"\"loggedIn\":%s,\"username\":",user?"true":"false");string(&j,user?user->username:"");append(&j,",\"game\":");string(&j,game?game->title:"");append(&j,",\"message\":");string(&j,message);append(&j,",\"unlocked\":%u,\"total\":%u,\"hardcore\":false}",summary.num_unlocked_achievements,summary.num_core_achievements);return json_result(env,&j);
}
JNIEXPORT jbyteArray JNICALL JNI_METHOD(achievementList)(JNIEnv* env,jobject self){
    (void)self;Json j={0};append(&j,"[");int count=0;
    rc_client_achievement_list_t* list=client?rc_client_create_achievement_list(client,RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE,RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE):NULL;
    if(list){for(uint32_t b=0;b<list->num_buckets;b++)for(uint32_t n=0;n<list->buckets[b].num_achievements;n++){
        const rc_client_achievement_t* a=list->buckets[b].achievements[n];if(count++)append(&j,",");append(&j,"{\"title\":");string(&j,a->title);append(&j,",\"description\":");string(&j,a->description);append(&j,",\"progress\":");string(&j,a->measured_progress);append(&j,",\"badge\":");string(&j,a->unlocked?a->badge_url:a->badge_locked_url);append(&j,",\"points\":%u,\"unlocked\":%s}",a->points,a->unlocked?"true":"false");
    }rc_client_destroy_achievement_list(list);}append(&j,"]");return json_result(env,&j);
}
JNIEXPORT jbyteArray JNICALL JNI_METHOD(saveProgress)(JNIEnv* env,jobject self){
    (void)self;if(!client)return NULL;size_t n=rc_client_progress_size(client);uint8_t* data=malloc(n);if(!data)return NULL;
    int result=rc_client_serialize_progress_sized(client,data,n);jbyteArray output=result==RC_OK?bytes(env,data,n):NULL;free(data);return output;
}
JNIEXPORT void JNICALL JNI_METHOD(loadProgress)(JNIEnv* env,jobject self,jbyteArray data){
    (void)self;if(!client)return;if(!data){rc_client_reset(client);return;}
    jsize n=(*env)->GetArrayLength(env,data);jbyte* p=(*env)->GetByteArrayElements(env,data,NULL);
    if(rc_client_deserialize_progress_sized(client,(uint8_t*)p,(size_t)n)!=RC_OK)rc_client_reset(client);
    (*env)->ReleaseByteArrayElements(env,data,p,JNI_ABORT);
}
