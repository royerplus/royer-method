import sys, os, json, math, wave
sys.path.insert(0,r'C:/Users/PC/Downloads/royer-method-work/python')
import cv2,numpy as np
p=r'C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017';out=p+'/analisis'
cap=cv2.VideoCapture(p+'/entrada/2a26720d76fd0eb0fd11701ac1b063b1.mp4');diffs=[];last=None
for i in range(595):
 ok,fr=cap.read()
 if not ok:break
 small=cv2.resize(fr,(144,144)).astype(float)
 if last is not None:diffs.append({'frame':i,'s':i/30,'mae':float(abs(small-last).mean())})
 last=small
cap.release()
changes=sorted(diffs,key=lambda x:-x['mae'])[:24]
print(json.dumps(changes,indent=2))
# Re-estimate repeating subdivision by fitting onset-grid in strongly periodic section8.2–14.95.
r=json.load(open(out+'/ritmo.json')); ts=np.array([q['s'] for q in r['onsets'] if 8.2<=q['s']<=14.95]); ks=np.round((ts-ts[0])/.279)
slope,intercept=np.polyfit(ks,ts,1)
r['regular_section_fit']={'section_s':[8.2,14.95],'subdivision_s':float(slope),'phase_s':float(intercept),'rms_error_s':float(np.sqrt(np.mean((ts-(intercept+ks*slope))**2))),'quarter_note_bpm_if_two_subdivisions':float(60/(2*slope)),'confidence':'high regular subdivision; medium beat interpretation; not audited by listening'}
json.dump(r,open(out+'/ritmo.json','w'),indent=2)
json.dump(changes,open(out+'/cambios-mayores.json','w'),indent=2)
print(json.dumps(r['regular_section_fit']))

