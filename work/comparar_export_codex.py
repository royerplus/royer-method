from pathlib import Path
import sys,subprocess,json
BASE=Path(__file__).parent
sys.path.insert(0,str(BASE/'python'))
import numpy as np
FF=BASE/'python/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
size=576*768*3
def abrir(path):
    return subprocess.Popen([str(FF),'-v','error','-threads','1','-i',path,'-map','0:v:0','-vsync','0','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
left,right=sys.argv[1:3];a,b=abrir(left),abrir(right);values=[];total=0
try:
    while True:
        x=a.stdout.read(size);y=b.stdout.read(size)
        if not x and not y:break
        if len(x)!=size or len(y)!=size:raise RuntimeError('No coincide la cantidad/tamaño de fotogramas')
        delta=np.abs(np.frombuffer(x,np.uint8).astype(np.int16)-np.frombuffer(y,np.uint8).astype(np.int16))
        acc=int(delta.sum(dtype=np.int64));total+=acc;values.append(acc/size)
finally:
    a.stdout.close();b.stdout.close()
    for p in [a,b]:
        err=p.stderr.read().decode(errors='replace');code=p.wait()
        if code:raise RuntimeError(err)
mae=total/(len(values)*size)
result=dict(base=left,nuevo=right,fotogramas=len(values),mae_0_255=mae,mae_normalizado=mae/255,umbral_normalizado=1/255,menor_que_1_de_255=mae<1,peor_fotograma=int(np.argmax(values)),mae_peor_fotograma=max(values),mae_por_fotograma=values)
Path(sys.argv[3]).write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k!='mae_por_fotograma'},indent=2))
assert len(values)==411 and mae<1,result
