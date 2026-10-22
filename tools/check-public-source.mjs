import fs from 'node:fs';import {execFileSync} from 'node:child_process';
const root=process.cwd(),paths=execFileSync('git',['ls-files','-z'],{encoding:'utf8'}).split('\0').filter(Boolean),problems=[];
const forbiddenPath=/(?:^|\/)(?:local\.properties|keystore\.properties|\.env(?:\..*)?|\.publish|\.gradle|\.kotlin|releases|build)(?:\/|$)|\.(?:apk|aab|jks|keystore|p12|gba|gbc|gb|sav|state)$/i;
let texts=0,binaries=0,submodules=0;
for(const path of paths){
 if(forbiddenPath.test(path)){problems.push(path);continue;}
 if(!fs.existsSync(path)){problems.push(path+' (missing)');continue;}
 if(fs.statSync(path).isDirectory()){submodules++;continue;}
 const bytes=fs.readFileSync(path);
 if(bytes.includes(0)){binaries++;continue;}texts++;
 const text=bytes.toString('utf8');
 if(/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\bghp_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{40,}\b|\bAKIA[A-Z0-9]{16}\b/.test(text))problems.push(path+' (credential pattern)');
 if(/[CD]:[\\/]Users[\\/]|[CD]:[\\/]CodexTools[\\/]/i.test(text))problems.push(path+' (machine-specific path)');
}
if(problems.length)throw Error('Publication scan failed; inspect these paths: '+problems.join(', '));
console.log(`Publication scan passed: ${texts} text files, ${binaries} binary source/assets, ${submodules} pinned submodules.`);
console.log('Pattern-based scan only; original notices and binary origins still require review.');
