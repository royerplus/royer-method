from PIL import Image
from pathlib import Path
import numpy as np
for p in sorted(Path(r'C:\Users\PC\Downloads\royer-method-work\testprueba1').glob('*.jpg'))[:5]:
 a=np.array(Image.open(p)); bright=(a[:,:,:3]>235).all(axis=2).mean(axis=1); dark=(a[:,:,:3]<50).all(axis=2).mean(axis=1)
 runs=[]
 for kind,v in [('blanco',bright),('oscuro',dark)]:
  active=v>.94; start=None
  for i,b in enumerate(active):
   if b and start is None:start=i
   if not b and start is not None:
    if i-start>=10:runs.append((kind,start,i))
    start=None
  if start is not None:runs.append((kind,start,len(active)))
 print(p.name,runs)