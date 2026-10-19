const params=new URLSearchParams(location.search);
const testId=params.get('test')||crypto.randomUUID();
if(!params.has('test')){params.set('test',testId);history.replaceState(null,'',`${location.pathname}?${params}`);}
const room=`test-${testId.slice(0,12)}`;
const frames=[];const states=new Map();
for(let i=1;i<=4;i++){
  const frame=document.createElement('iframe');
  frame.title=`测试玩家 ${i} 的牌桌`;
  const args=new URLSearchParams({profile:`${testId}-${i}`,seat:String(i),room,auto:i===1?'create':'join',compact:'1'});
  frame.src=`./index.html?${args}`;
  frames.push(frame);document.getElementById('frames').append(frame);
}
window.addEventListener('message',event=>{
  if(event.origin!==location.origin||!frames.some(frame=>frame.contentWindow===event.source)||event.data?.type!=='LUZHOU_TEST_STATE')return;
  states.set(event.data.profile,event.data);
  const joined=[...states.values()].filter(s=>s.players===4).length;
  const playing=[...states.values()].some(s=>s.inGame);
  document.getElementById('test-status').textContent=playing?`房间 ${room} · 牌局进行中`:`房间 ${room} · ${joined}/4 个窗口已同步`;
  document.getElementById('all-ready').disabled=joined<4||playing;
});
document.getElementById('all-ready').addEventListener('click',()=>{
  frames.forEach(frame=>frame.contentWindow.postMessage({type:'LUZHOU_TEST_READY'},location.origin));
});
document.getElementById('new-table').addEventListener('click',()=>{location.href=`${location.pathname}?test=${crypto.randomUUID()}`;});
