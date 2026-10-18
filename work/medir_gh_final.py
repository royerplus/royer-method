from pathlib import Path
import sys,json,math
sys.path.insert(0,str(Path(__file__).parent/'python'))
import cv2,numpy as np
from PIL import Image,ImageDraw
ROOT=Path(__file__).parent/'analisis/forense'
def load(n):return cv2.imread(str(ROOT/f'f-{n-140:04d}.png'))
sift=cv2.SIFT_create(nfeatures=3500,contrastThreshold=.012,edgeThreshold=14)
bf=cv2.BFMatcher(cv2.NORM_L2)
mask=np.zeros((768,576),np.uint8);mask[15:620,20:556]=255
shots={}
for shot,stable,windows in [('G',248,[(232,254),(342,364)]),('H',260,[(255,268),(365,378)])]:
    k0,d0=sift.detectAndCompute(load(stable),mask);poses=[]
    for lo,hi in windows:
      for n in range(lo,hi+1):
        k,d=sift.detectAndCompute(load(n),mask)
        if d is None:continue
        matches=sorted([a for a,b in bf.knnMatch(d0,d,k=2) if a.distance<.73*b.distance],key=lambda m:m.distance)
        unique=[];seen=set()
        for m in matches:
          if m.trainIdx not in seen:seen.add(m.trainIdx);unique.append(m)
        if len(unique)<6:continue
        src=np.float32([k0[m.queryIdx].pt for m in unique]);dst=np.float32([k[m.trainIdx].pt for m in unique])
        m,ins=cv2.estimateAffinePartial2D(src,dst,method=cv2.RANSAC,ransacReprojThreshold=3,maxIters=5000,confidence=.999)
        if m is None or ins.sum()<6:continue
        s=float(np.hypot(m[0,0],m[1,0]));a=float(math.degrees(math.atan2(m[1,0],m[0,0])))
        if not .4<s<4:continue
        center=m[:,:2]@np.array([288,384])+m[:,2]
        poses.append(dict(frame=n,escala=s,rotacion=a,x=float((center[0]-288)/576),y=float((center[1]-384)/768),inliers=int(ins.sum()),confianza=round(float(ins.sum())/len(unique),3)))
    shots[shot]=dict(stableFrame=stable,poses=poses)
out=dict(shots=shots,final=[])
sheet=Image.new('RGB',(8*144,5*220),'#222222');draw=ImageDraw.Draw(sheet)
for i,n in enumerate(range(374,411)):
    bgr=load(n)
    if bgr is None:continue
    im=Image.fromarray(cv2.cvtColor(bgr,cv2.COLOR_BGR2RGB));arr=np.asarray(im)[20:620,20:556]
    out['final'].append(dict(frame=n,mean=float(arr.mean()),std=float(arr.std()),rgb=arr.mean(axis=(0,1)).tolist()))
    sheet.paste(im.resize((144,192)),((i%8)*144,(i//8)*220));draw.text(((i%8)*144+2,(i//8)*220+195),f'{n} {n/30:.3f}',fill='white')
sheet.save(ROOT/'final-detalle.jpg')
(ROOT/'gh-final-mediciones.json').write_text(json.dumps(out,indent=2))
for k,v in shots.items():print(k,[(p['frame'],p['inliers'],round(p['escala'],3),round(p['x'],3),round(p['y'],3)) for p in v['poses']])
print('final',out['final'])
