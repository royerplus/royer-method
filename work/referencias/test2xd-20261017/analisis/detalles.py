import sys, os, json
sys.path.insert(0,r'C:/Users/PC/Downloads/royer-method-work/python')
import cv2,numpy as np
p=r'C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017';cap=cv2.VideoCapture(p+'/entrada/2a26720d76fd0eb0fd11701ac1b063b1.mp4');out=p+'/analisis'
for name,start,step,count,crop in [('ojos_movimiento',6.8,.1,24,(120,0,400,280)),('construccion',9.1,.1,24,None),('settings',13.3,.2,15,None)]:
 W=280 if crop else 288;H=W+28;rows=(count+5)//6;canvas=np.full((rows*H,6*W,3),30,np.uint8)
 for i in range(count):
  t=start+step*i;cap.set(cv2.CAP_PROP_POS_MSEC,t*1000);ok,fr=cap.read()
  if not ok:continue
  if crop:x1,y1,x2,y2=crop;fr=fr[y1:y2,x1:x2]
  fr=cv2.resize(fr,(W,W));x=i%6*W;y=i//6*H;canvas[y:y+W,x:x+W]=fr
  cv2.putText(canvas,f'{t:.2f}s',(x+5,y+W+21),cv2.FONT_HERSHEY_SIMPLEX,.55,(255,255,255),1,cv2.LINE_AA)
 cv2.imwrite(out+'/'+name+'.jpg',canvas)
cap.set(cv2.CAP_PROP_POS_MSEC,14000);ok,fr=cap.read();cv2.imwrite(out+'/settings-original-14s.png',fr)
cap.release()

