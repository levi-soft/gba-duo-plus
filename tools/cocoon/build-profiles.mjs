import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const root=fileURLToPath(new URL('../../',import.meta.url));
export const lock=JSON.parse(fs.readFileSync(new URL('profiles.lock.json',import.meta.url)));
export const editions=Object.freeze([
  Object.freeze({name:'GBA Duo Plus',package:'com.khumam.gbaduo.plus'}),
  Object.freeze({name:'GBA Duo Plus SS',package:'com.khumam.gbaduo.plus.ss'}),
]);
const sha=(value,algorithm='sha256')=>createHash(algorithm).update(value).digest('hex');

export function gitBlobHash(bytes){
  return sha(Buffer.concat([Buffer.from('blob '+bytes.length+'\0'),bytes]),'sha1');
}

/** Only new player identities are imported. Existing player definitions belong to Cocoon. */
export function additiveProfile(upstream,profile){
  assert.equal(upstream.databaseVersion,14);
  assert.equal(upstream.platform.uniqueId,profile.id);
  assert.equal(upstream.revisionNumber,profile.revision);
  const official=upstream.playerList.find(player=>player.uniqueId===profile.officialPlayerId);
  assert(official,'The pinned upstream platform must contain its official GBADuo player');
  assert(official.amStartArguments.includes('com.khumam.gbaduo/com.khumam.gbaduo.library.LaunchActivity'));
  assert(official.amStartArguments.includes('-d {file.uri}'));
  return {
    databaseVersion:upstream.databaseVersion,
    revisionNumber:upstream.revisionNumber,
    platform:structuredClone(upstream.platform),
    playerList:editions.map(edition=>({
      name:edition.name,
      uniqueId:profile.id+'.'+edition.package,
      description:'Supported extensions: '+profile.extensions.join(', ')+'.',
      acceptedFilenameRegex:'(?i)^.*\\.(?:'+profile.extensions.join('|')+')$',
      amStartArguments:'-a android.intent.action.VIEW\n -n '+edition.package+'/com.khumam.gbaduo.library.LaunchActivity\n -d {file.uri}',
      killPackageProcesses:false,
      killPackageProcessesWarning:false,
      extra:'',
    })),
  };
}

export async function fetchUpstream(profile){
  const url='https://raw.githubusercontent.com/inssekt/CocoonFE/'+lock.commit+'/platforms/'+profile.file;
  const result=await fetch(url,{signal:AbortSignal.timeout(30000)});
  assert.equal(result.status,200,'Could not read pinned Cocoon platform '+profile.file);
  const bytes=Buffer.from(await result.arrayBuffer());
  assert(bytes.length<=256*1024,'Unexpectedly large platform definition');
  assert.equal(gitBlobHash(bytes),profile.gitBlobSha1,'Pinned upstream platform bytes changed');
  return {url,bytes,data:JSON.parse(bytes)};
}

async function main(){
  let out=path.join(root,'reports/cocoon-import-profiles-0.1.9');
  for(let i=2;i<process.argv.length;i++){
    assert.equal(process.argv[i],'--out','Use --out <new-directory>');
    out=path.resolve(process.argv[++i]||'');
  }
  assert(!fs.existsSync(out),'Output already exists; choose a new directory. No file was overwritten.');
  // Validate every upstream source before creating an output directory.
  const fetched=await Promise.all(lock.platforms.map(async profile=>({profile,source:await fetchUpstream(profile)})));
  const outputs=fetched.map(({profile,source})=>{
    const payload=additiveProfile(source.data,profile);
    const bytes=Buffer.from(JSON.stringify(payload,null,2)+'\n');
    return {profile,source,payload,bytes,name:'GBA-Duo-Plus-'+profile.id.toUpperCase()+'.json'};
  });
  fs.mkdirSync(out,{recursive:true});
  for(const row of outputs)fs.writeFileSync(path.join(out,row.name),row.bytes,{flag:'wx'});
  const manifest={
    kind:'additive-Cocoon-player-import-profiles',
    sourceRepository:lock.repository,sourceBranch:lock.branch,sourceCommit:lock.commit,
    sourceLicenseNotice:lock.licenseNotice,
    files:outputs.map(row=>({file:row.name,sha256:sha(row.bytes),bytes:row.bytes.length,platformId:row.profile.id,sourceUrl:row.source.url,sourceGitBlobSha1:row.profile.gitBlobSha1,officialPlatformMetadataPreserved:true,newPlayers:row.payload.playerList.map(p=>({id:p.uniqueId,package:p.amStartArguments.match(/-n ([^/]+)/)[1]}))})),
    userLibraryModified:false,defaultPlayerSelected:false,deviceUiVerified:false,
    activation:'Cocoon Settings > Library & data > scroll to platforms > Y/+ > Add Platforms > scroll to the last row > Import Custom Platform; then choose the installed Plus edition as Default player and save with Y/check.',
  };
  fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n',{flag:'wx'});
  console.log(JSON.stringify({directory:out,platforms:outputs.length,newPlayerDefinitions:outputs.length*editions.length,upstreamBytesVerified:true,userLibraryModified:false,deviceUiVerified:false}));
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(error=>{console.error(error.message);process.exitCode=1;});
