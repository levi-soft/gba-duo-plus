import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';

const root=path.resolve('site');
const base='https://levi-soft.github.io/gba-duo-plus/';
const titles=new Set();
const descriptions=new Set();
const sitemap=fs.readFileSync(path.join(root,'sitemap.xml'),'utf8');
for(const [file,name,version] of [['index.html','GBA Duo Plus','0.1.9-plus.2'],['single-screen/index.html','GBA Duo Plus SS','0.1.9-plus.2-ss']]){
  const html=fs.readFileSync(path.join(root,file),'utf8');
  const canonical=base+(file==='index.html'?'':'single-screen/');
  assert.equal([...html.matchAll(/<h1\b/g)].length,1,file+' must have one H1');
  assert(html.includes(`rel="canonical" href="${canonical}"`));
  assert(html.includes('name="robots" content="index,follow"'));
  assert(sitemap.includes(`<loc>${canonical}</loc>`));
  const title=html.match(/<title>([^<]+)<\/title>/)?.[1];
  const description=html.match(/name="description" content="([^"]+)"/)?.[1];
  assert(title?.startsWith(name));titles.add(title);
  assert(description?.length>=100&&description.length<=180);descriptions.add(description);
  const data=JSON.parse(html.match(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/)[1]);
  assert.equal(data.name,name);assert.equal(data.url,canonical);assert.equal(data.softwareVersion,version);
  assert(data.downloadUrl.startsWith('https://github.com/levi-soft/gba-duo-plus/releases/download/'));
  assert(!data.aggregateRating,'Do not invent product ratings');
  assert(html.includes('https://levi-soft.github.io/gba-duo-companion-builder/'));
  for(const match of html.matchAll(/(?:href|src)="([^"]+)"/g)){
    const url=match[1];if(/^(?:https:|#)/.test(url))continue;
    let target=path.resolve(root,path.dirname(file),url.split(/[?#]/)[0]);
    assert(target.startsWith(root+path.sep)||target===root,'Link escapes public site');
    if(fs.existsSync(target)&&fs.statSync(target).isDirectory())target=path.join(target,'index.html');
    assert(fs.existsSync(target),file+' missing link '+url);
  }
}
assert.equal(titles.size,2);assert.equal(descriptions.size,2);
assert(!sitemap.includes('releases/download'),'Sitemap should contain HTML landing pages');
console.log('Public site: metadata, schemas, local links, edition downloads and sitemap PASS');
