from pathlib import Path
import subprocess, sys
from PIL import Image, ImageDraw, ImageOps
base=Path(__file__).parent
ff=base/'python/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
video=base/'resultados/replica-testprueba1.mp4'
out=base/'analisis/exportado'
out.mkdir(exist_ok=True)
subprocess.run([str(ff),'-y','-v','error','-i',str(video),'-ss','4.8','-vf','fps=10,scale=216:-1',str(out/'cuadro-%04d.jpg')],check=True)
frames=sorted(out.glob('cuadro-*.jpg'))
for start in range(0,len(frames),40):
    take=frames[start:start+40]
    canvas=Image.new('RGB',(8*180,((len(take)+7)//8)*265),'#171717')
    draw=ImageDraw.Draw(canvas)
    for j,path in enumerate(take):
        im=ImageOps.contain(Image.open(path).convert('RGB'),(176,235))
        x=(j%8)*180+(180-im.width)//2
        y=(j//8)*265
        canvas.paste(im,(x,y))
        draw.text(((j%8)*180+5,y+241),f'{4.8+(start+j)/10:.3f}s',fill='white')
    canvas.save(out/f'hoja-{start//40+1}.jpg')
print('hojas',len(frames))
