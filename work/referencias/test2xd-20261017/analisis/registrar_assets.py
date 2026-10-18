"""Análisis geométrico, sin escribir ni transformar ninguna imagen."""
from pathlib import Path
import sys,json,math
sys.path.insert(0,'C:/Users/PC/Downloads/royer-method-work/python')
import cv2
import numpy as np
P=Path(__file__).resolve().parents[1]
ref=cv2.imread(str(P/'entrada/Screenshot_20260623-190945.jpg'))
h,w=ref.shape[:2]
sift=cv2.SIFT_create(nfeatures=7000,contrastThreshold=.015,edgeThreshold=15)
kr,dr=sift.detectAndCompute(cv2.cvtColor(ref,cv2.COLOR_BGR2GRAY),None)
results={}
for path in sorted((P/'assets').glob('*.png')):
    a=cv2.imread(str(path),cv2.IMREAD_UNCHANGED)
    hh,ww=a.shape[:2]
    mask=cv2.erode((a[:,:,3]>220).astype('uint8')*255,np.ones((9,9),np.uint8))
    ka,da=sift.detectAndCompute(cv2.cvtColor(a[:,:,:3],cv2.COLOR_BGR2GRAY),mask)
    raw=cv2.BFMatcher().knnMatch(da,dr,k=2)
    good=[m for m,n in raw if m.distance<.76*n.distance]
    src=np.float32([ka[m.queryIdx].pt for m in good])
    dst=np.float32([kr[m.trainIdx].pt for m in good])
    item={'asset_size':[ww,hh],'features_asset':len(ka),'features_original':len(kr),'matches_ratio_076':len(good)}
    if len(good)>=3:
        M,inliers=cv2.estimateAffinePartial2D(src,dst,method=cv2.RANSAC,ransacReprojThreshold=5,maxIters=10000,confidence=.999,refineIters=50)
        if M is not None:
            ii=inliers.ravel().astype(bool)
            pred=src@M[:,:2].T+M[:,2]
            residual=np.linalg.norm(pred-dst,axis=1)
            scale=float(np.linalg.norm(M[:,0])); angle=float(math.degrees(math.atan2(M[1,0],M[0,0])))
            center=M@np.array([ww/2,hh/2,1.])
            norm=np.diag([1/w,1/h,1.])
            M3=np.vstack([M,[0,0,1]])
            # Asset uv -> original uv.
            U=norm@M3@np.diag([ww,hh,1.])
            neck=np.linalg.inv(U)@np.array([.55,.62,1.])
            bbox=cv2.boundingRect(src[ii]) if ii.sum() else (0,0,0,0)
            item.update(matrix_asset_pixel_to_original_pixel=M.tolist(),matrix_asset_uv_to_original_uv=U[:2,:].tolist(),inliers=int(ii.sum()),inlier_fraction=float(ii.mean()),residual_median_px=float(np.median(residual[ii])),residual_max_px=float(np.max(residual[ii])),asset_inlier_bbox_xywh=list(bbox),scale_px=scale,rotation_degrees=angle,center_in_original_uv=[float(center[0]/w),float(center[1]/h)],neck_asset_uv=neck[:2].tolist(),encuadre_proposed={'escala':.84*scale*ww/w,'x':.84*(center[0]/w-.5),'y':.84*(center[1]/h-.5),'rotacion':angle},matches=[{'asset':src[j].tolist(),'original':dst[j].tolist(),'error_px':float(residual[j]),'inlier':bool(ii[j])} for j in range(len(good))])
    results[path.name]=item
out={'original_size':[w,h],'original_display_scale':.84,'method':'SIFT; Lowe ratio .76; similarity RANSAC threshold5px; alpha>220 eroded9px; unverified until native composite','assets':results}
(P/'analisis/registro-sift.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf8')
for n,v in results.items(): print(n,json.dumps({k:x for k,x in v.items() if k not in ['matches']},ensure_ascii=False))
