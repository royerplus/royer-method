import sys, os, json, wave, math
sys.path.insert(0,r'C:/Users/PC/Downloads/royer-method-work/python')
import numpy as np, cv2
p=r'C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/analisis'
w=wave.open(p+'/audio-mono.wav','rb'); sr=w.getframerate(); x=np.frombuffer(w.readframes(w.getnframes()),np.int16).astype(np.float64)/32768;w.close()
hop=220;nfft=2048
frames=np.lib.stride_tricks.sliding_window_view(x,nfft)[::hop]
spec=abs(np.fft.rfft(frames*np.hanning(nfft),axis=1))
freq=np.fft.rfftfreq(nfft,1/sr)
# Positive spectral change, whitened by per-frequency average so bass alone does not dominate.
wh=spec/(np.mean(spec,axis=0)+.03)
flux=np.maximum(np.diff(wh,axis=0,prepend=wh[:1]),0).mean(axis=1)
flux=np.convolve(flux,[.15,.7,.15],mode='same')
times=(np.arange(len(frames))*hop+nfft/2)/sr
rms=np.sqrt((frames**2).mean(axis=1))
bass=np.sqrt((spec[:,(freq>=35)&(freq<=160)]**2).mean(axis=1))
nov=(flux-np.percentile(flux,30)).clip(0)
valid=(times>=.2)&(times<=15.5)
f=nov.copy();f[~valid]=0
# onset candidates in descending salience then enforce spacing.
candidates=[i for i in range(1,len(f)-1) if f[i]>=f[i-1] and f[i]>=f[i+1] and f[i]>np.percentile(f[valid],65)]
keep=[]
for i in sorted(candidates,key=lambda i:-f[i]):
 if all(abs(times[i]-times[j])>=.17 for j in keep):keep.append(i)
keep=sorted(keep)
# Tempo autocorrelation of novelty, excluding tiktokoutro.
a=f[valid]; a=a-a.mean(); ac=np.correlate(a,a,'full')[len(a)-1:];ac=ac/ac[0]
tempos=[]
for lag in range(int(60/190*sr/hop),int(60/65*sr/hop)):
 if ac[lag]>=ac[lag-1] and ac[lag]>=ac[lag+1]:tempos.append({'bpm':60*sr/hop/lag,'correlation':float(ac[lag]),'lag_s':lag*hop/sr})
tempos=sorted(tempos,key=lambda z:-z['correlation'])[:8]
result={'sample_rate':sr,'audio_duration_s':len(x)/sr,'analysis_method':'PCM22050mono spectralflux STFT2048 hop220; onsetminimumspacing0.17s; autocorrelation0.2–15.5s','tempo_candidates':tempos,'onsets':[{'s':round(float(times[i]),4),'strength':round(float(f[i]),4),'rms':round(float(rms[i]),4)}for i in keep], 'rms_peak':float(rms.max())}
json.dump(result,open(p+'/ritmo.json','w'),indent=2)
W=1800;H=900;img=np.full((H,W,3),240,np.uint8)
for panel,arr,color,label in [(0,rms,(90,70,20),'AudioRMS'),(1,nov,(20,90,200),'Spectralonsets'),(2,bass,(180,60,90),'Bass35-160Hz')]:
 y0=panel*300; ymax=np.percentile(arr,99.5) or 1
 pts=np.array([[int(t/20*(W-80))+60,int(y0+270-min(v/ymax,1)*220)]for t,v in zip(times,arr)],np.int32)
 cv2.polylines(img,[pts],False,color,1)
 cv2.putText(img,label,(15,y0+25),cv2.FONT_HERSHEY_SIMPLEX,.6,(30,30,30),1,cv2.LINE_AA)
 for sec in range(20):
  xx=int(sec/20*(W-80))+60;cv2.line(img,(xx,y0+40),(xx,y0+275),(210,210,210),1);cv2.putText(img,str(sec),(xx,y0+295),cv2.FONT_HERSHEY_SIMPLEX,.4,(30,30,30),1)
 if panel==1:
  for i in keep:xx=int(times[i]/20*(W-80))+60;cv2.circle(img,(xx,int(y0+270-min(nov[i]/ymax,1)*220)),4,(0,0,0),-1)
cv2.imwrite(p+'/ritmo-contacto.png',img)
print(json.dumps(result,indent=2))

