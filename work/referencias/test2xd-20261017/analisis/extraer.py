import sys, os, json, subprocess, wave
sys.path.insert(0, r'C:/Users/PC/Downloads/royer-method-work/python')
import cv2, numpy as np
p=r'C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017'
v=p+'/entrada/2a26720d76fd0eb0fd11701ac1b063b1.mp4'
out=p+'/analisis'
ff=r'C:/Users/PC/Downloads/royer-method-work/python/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
cap=cv2.VideoCapture(v)
meta={'fps':cap.get(cv2.CAP_PROP_FPS),'width':int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)),'height':int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)),'frames':int(cap.get(cv2.CAP_PROP_FRAME_COUNT))}
meta['video_duration_s']=meta['frames']/meta['fps']
times=np.arange(0,meta['video_duration_s'],.5)
for page in range(2):
 ts=times[page*20:(page+1)*20]
 if not len(ts): continue
 contact=np.full((4*320,5*288,3),28,dtype=np.uint8)
 for i,t in enumerate(ts):
  cap.set(cv2.CAP_PROP_POS_MSEC,t*1000)
  ok,fr=cap.read()
  if not ok: continue
  cv2.imwrite(out+f'/frame_{t:05.2f}.jpg',fr)
  fr=cv2.resize(fr,(288,288))
  x=(i%5)*288;y=(i//5)*320
  contact[y:y+288,x:x+288]=fr
  cv2.putText(contact,f'{t:.2f}s',(x+8,y+311),cv2.FONT_HERSHEY_SIMPLEX,.6,(255,255,255),1,cv2.LINE_AA)
 cv2.imwrite(out+f'/contacto_{page+1}.jpg',contact)
cap.release()
subprocess.run([ff,'-y','-hide_banner','-loglevel','error','-i',v,'-vn','-ac','1','-ar','22050',out+'/audio-mono.wav'],check=True)
json.dump(meta,open(out+'/metadatos.json','w'),indent=2)
print(json.dumps(meta))
