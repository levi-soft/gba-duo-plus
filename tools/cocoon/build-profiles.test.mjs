import test from 'node:test';
import assert from 'node:assert/strict';
import {lock,editions,additiveProfile,gitBlobHash} from './build-profiles.mjs';

function fixture(profile){return {databaseVersion:14,revisionNumber:profile.revision,platform:{name:profile.id,uniqueId:profile.id,shortname:profile.id,extra:'keep official metadata'},playerList:[{uniqueId:profile.officialPlayerId,amStartArguments:'-a android.intent.action.VIEW\n -n com.khumam.gbaduo/com.khumam.gbaduo.library.LaunchActivity\n -d {file.uri}'},{uniqueId:profile.id+'.user.custom',amStartArguments:'keep user player'}]};}

test('three additive platform imports keep native platform identity and leave the input catalogue untouched',()=>{
  assert.deepEqual(lock.platforms.map(p=>p.id),['gb','gbc','gba']);
  for(const profile of lock.platforms){
    const source=fixture(profile),before=structuredClone(source),out=additiveProfile(source,profile);
    assert.deepEqual(source,before);assert.deepEqual(out.platform,source.platform);assert.notEqual(out.platform,source.platform);
    assert.equal(out.platform.uniqueId,profile.id);assert.equal(out.playerList.length,2);
    assert(out.playerList.every(p=>!source.playerList.some(old=>old.uniqueId===p.uniqueId)));
    assert(!Object.hasOwn(out,'defaultPlayer'));assert(!Object.hasOwn(out,'romFolders'));
    for(const p of out.playerList){assert(!Object.hasOwn(p,'isDefault'));assert.equal(p.killPackageProcesses,false);}
  }
});

test('six release package identities use the official VIEW/content-URI launch protocol',()=>{
  const ids=new Set();
  for(const profile of lock.platforms){
    const out=additiveProfile(fixture(profile),profile);
    for(let i=0;i<editions.length;i++){
      const p=out.playerList[i],edition=editions[i];
      assert(!ids.has(p.uniqueId));ids.add(p.uniqueId);
      assert.equal(p.uniqueId,profile.id+'.'+edition.package);
      assert.equal(p.amStartArguments,'-a android.intent.action.VIEW\n -n '+edition.package+'/com.khumam.gbaduo.library.LaunchActivity\n -d {file.uri}');
      assert(!p.amStartArguments.includes('com.khumam.gbaduo/com.khumam'));
      assert(!p.amStartArguments.includes('.debug'));assert(!p.amStartArguments.includes('{file.path}'));
    }
  }
  assert.equal(ids.size,6);
});

test('extension lists accept the app-supported formats without advertising unsupported archives',()=>{
  for(const profile of lock.platforms){
    const payload=additiveProfile(fixture(profile),profile);
    for(const p of payload.playerList){
      const re=new RegExp(p.acceptedFilenameRegex.replace(/^\(\?i\)/,''),'i');
      for(const ext of profile.extensions)assert(re.test('Example.'+ext.toUpperCase()));
      for(const ext of ['7z','rar','exe','apk','sav'])assert(!re.test('Example.'+ext));
    }
  }
});

test('source identity checks reject unexpected databases or platforms',()=>{
  const profile=lock.platforms[2];
  for(const update of [{databaseVersion:99},{platform:{uniqueId:'another'}},{revisionNumber:999},{playerList:[]}])assert.throws(()=>additiveProfile({...fixture(profile),...update},profile));
  assert.equal(gitBlobHash(Buffer.from('hello\n')),'ce013625030ba8dba906f756967f9e9ca394464a');
});
