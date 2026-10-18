from pathlib import Path
import sys,json
sys.path.insert(0,str(Path(__file__).parent/'python'))
import cv2,numpy as np
root=Path(__file__).parent/'analisis/forense'
out=[]
for n in range(149,161):
    im=cv2.imread(str(root/f'f-{n-140:04d}.png'))
    hsv=cv2.cvtColor(im,cv2.COLOR_BGR2HSV)
    colorful=(hsv[:,40:536,1]>45)&(hsv[:,40:536,2]>35)&(hsv[:,40:536,0]<40)
    row=colorful.mean(axis=1)
    colored=(row>.11).astype(np.uint8)
    colored=cv2.morphologyEx(colored[:,None],cv2.MORPH_CLOSE,np.ones((7,1),np.uint8)).ravel()
    top=np.where(colored[:384]>0)[0]
    bottom=np.where(colored[384:]>0)[0]+384
    yt=int(top[-1]+1) if len(top) else 0
    yb=int(bottom[0]) if len(bottom) else 768
    out.append(dict(frame=n,t=n/30,top=yt,bottom=yb,gap=yb-yt,upperOffset=(yt-384)/768,lowerOffset=(yb-384)/768))
print(json.dumps(out,indent=2))
(root/'entrada-mitad-mediciones.json').write_text(json.dumps(out,indent=2))
