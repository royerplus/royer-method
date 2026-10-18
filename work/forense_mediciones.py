from pathlib import Path
import sys, json, csv, math
sys.path.insert(0,str(Path(__file__).parent/'python'))
import cv2
import numpy as np
ROOT=Path(__file__).parent/'analisis/forense'
def load(n): return cv2.imread(str(ROOT/f'f-{n-140:04d}.png'))
sift=cv2.SIFT_create(nfeatures=2400,contrastThreshold=.015,edgeThreshold=14)
mask=np.zeros((768,576),np.uint8);mask[20:620,35:545]=255
bf=cv2.BFMatcher(cv2.NORM_L2)
templates={'A':164,'B':179,'C':194,'D':208,'E':221,'F':231,'G':248,'H':260}
windows={'A':[(149,172),(261,282)],'B':[(172,187),(282,297)],'C':[(181,200),(291,310)],'D':[(200,214),(310,324)],'E':[(214,228),(324,338)],'F':[(228,241),(338,352)],'G':[(232,255),(342,365)],'H':[(255,269),(365,379)]}
results=[]
for shot,tn in templates.items():
    template=load(tn); kt,dt=sift.detectAndCompute(template,mask)
    for lo,hi in windows[shot]:
        for n in range(lo,hi+1):
            img=load(n)
            if img is None:continue
            ki,di=sift.detectAndCompute(img,mask)
            if di is None:continue
            matches=[a for a,b in bf.knnMatch(dt,di,k=2) if a.distance<.73*b.distance]
            for part in ['full','top','bottom']:
                use=[m for m in matches if part=='full' or (kt[m.queryIdx].pt[1]<384)==(part=='top')]
                if len(use)<4:continue
                src=np.float32([kt[m.queryIdx].pt for m in use]);dst=np.float32([ki[m.trainIdx].pt for m in use])
                m,ins=cv2.estimateAffinePartial2D(src,dst,method=cv2.RANSAC,ransacReprojThreshold=3.5,maxIters=3000,confidence=.995)
                if m is None:continue
                valid=ins.ravel().astype(bool); residual=np.linalg.norm((src@m[:,:2].T+m[:,2])-dst,axis=1)
                sx=float(np.hypot(m[0,0],m[1,0])); angle=float(math.degrees(math.atan2(m[1,0],m[0,0])))
                center=m[:,:2]@np.array([288,384])+m[:,2]
                results.append(dict(shot=shot,frame=n,t=n/30,part=part,inliers=int(valid.sum()),matches=len(use),error=float(np.median(residual[valid])),scale=sx,angle=angle,x=float((center[0]-288)/576),y=float((center[1]-384)/768),matrix=m.tolist()))
(ROOT/'mediciones.json').write_text(json.dumps(results,indent=2))
with (ROOT/'mediciones.csv').open('w',newline='') as f:
    w=csv.DictWriter(f,fieldnames=[k for k in results[0] if k!='matrix']);w.writeheader();w.writerows([{k:v for k,v in r.items() if k!='matrix'} for r in results])
for shot in templates:
    print(shot)
    for r in results:
        if r['shot']==shot and r['part']=='full' and r['frame']<270:
            print(f"{r['frame']:3} {r['t']:5.3f} N{r['inliers']:3} err{r['error']:4.2f} scale{r['scale']:5.3f} angle{r['angle']:6.2f} x{r['x']:6.3f} y{r['y']:6.3f}")
