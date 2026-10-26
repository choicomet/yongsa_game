import json,sys,cv2,numpy as np
from shapely.geometry import Polygon
from shapely.ops import unary_union
ROOT='assets/'
def P(f,w,h): return np.array([[f[i]*w,f[i+1]*h] for i in range(0,len(f),2)],np.float64)
def items():
    m=json.load(open(ROOT+'data/map.json')); s=json.load(open(ROOT+'data/natural-room-scenes.json'))
    out=[]
    for j in m['joints']:
        img=cv2.imread(ROOT+j['image']); out.append(('joint',j['id'],j['image'],j['walkable'],j.get('blocked',[]),j['rect'][2]/img.shape[1]))
    for p in m['passageScenes']: out.append(('passage',p['id'],p['image'],p['walkable'],p.get('blocked',[]),960/p['pixelWidth']))
    for sc in s['scenes']: out.append(('room',sc['id'],sc['image'],sc['walkable'],sc.get('blocked',[]),sc['suggestedWidthTiles']*32/sc['pixelWidth']))
    return out
def candidate(img,walk,blocked,wpp,tex=85,reach_w=72,foot_w=10):
    h,w=img.shape[:2]
    M=np.zeros((h,w),np.uint8); cv2.fillPoly(M,[P(walk,w,h).astype(np.int32)],1)
    B=np.zeros((h,w),np.uint8)
    for b in blocked: cv2.fillPoly(B,[P(b,w,h).astype(np.int32)],1)
    sm=cv2.medianBlur(img,7)
    hsv=cv2.cvtColor(sm,cv2.COLOR_BGR2HSV).astype(int)
    H,S,V=hsv[...,0],hsv[...,1],hsv[...,2]
    dirt=(H>=5)&(H<=22)&(S>=90)&(V>=70)&(V<=230)
    g=cv2.cvtColor(sm,cv2.COLOR_BGR2GRAY).astype(np.float32)
    gm=cv2.blur(np.hypot(cv2.Sobel(g,cv2.CV_32F,1,0),cv2.Sobel(g,cv2.CV_32F,0,1)),(11,11))
    dirt&=gm<np.percentile(gm[M>0],tex)
    d=cv2.morphologyEx(dirt.astype(np.uint8),cv2.MORPH_CLOSE,np.ones((7,7),np.uint8))
    r=max(3,int(foot_w/wpp))
    k=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*r+1,2*r+1))
    d=cv2.morphologyEx(d,cv2.MORPH_OPEN,k)
    dist=cv2.distanceTransform((1-M).astype(np.uint8),cv2.DIST_L2,5)
    d&=((dist<reach_w/wpp)&(B==0)).astype(np.uint8)
    d=cv2.morphologyEx(d,cv2.MORPH_OPEN,k)
    U=((d|M)>0).astype(np.uint8)
    n,lbl=cv2.connectedComponents(U)
    keep=set(np.unique(lbl[M>0]))-{0}
    add=(np.isin(lbl,list(keep))&(d>0)&(M==0)).astype(np.uint8)
    cs,_=cv2.findContours(add,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
    polys=[c.reshape(-1,2) for c in cs if cv2.contourArea(c)>(2.2*r)**2 and len(c)>=3]
    return M,polys
def merge(walk,polys,w,h,wpp):
    base=Polygon(P(walk,w,h))
    if not base.is_valid: base=base.buffer(0)
    adds=[Polygon(p).buffer(0) for p in polys]
    # overlap slightly so union is one piece
    u=unary_union([base]+[a.buffer(2) for a in adds if a.area>0])
    if u.geom_type!='Polygon':
        u=max(u.geoms,key=lambda g:g.area)
    u=Polygon(u.exterior).simplify(max(3,w*0.003),preserve_topology=True)
    u=unary_union([u,base])  # never lose original floor
    if u.geom_type!='Polygon': u=max(u.geoms,key=lambda g:g.area)
    u=Polygon(u.exterior)
    co=list(u.exterior.coords)[:-1]
    flat=[]
    for x,y in co: flat+= [round(min(max(x/w,0),1),4), round(min(max(y/h,0),1),4)]
    return flat,u
def overlay(img,M,newflat,path):
    h,w=img.shape[:2]; o=img.copy()
    N=np.zeros((h,w),np.uint8); cv2.fillPoly(N,[P(newflat,w,h).astype(np.int32)],1)
    o[M>0]=(o[M>0]*0.65+np.array([60,200,60])*0.35).astype(np.uint8)
    a=(N>0)&(M==0)
    o[a]=(o[a]*0.45+np.array([255,120,0])*0.55).astype(np.uint8)
    s=760/max(w,h); cv2.imwrite(path,cv2.resize(o,(int(w*s),int(h*s))),[cv2.IMWRITE_JPEG_QUALITY,85])
if __name__=='__main__':
    res={}
    for kind,id_,im,walk,bl,wpp in items():
        img=cv2.imread(ROOT+im); h,w=img.shape[:2]
        M,polys=candidate(img,walk,bl,wpp)
        if not polys: continue
        flat,u=merge(walk,polys,w,h,wpp)
        gain=u.area/Polygon(P(walk,w,h)).buffer(0).area-1
        if gain<0.004: continue
        res[id_]={'kind':kind,'walkable':flat,'gain':round(gain,3),'n':len(flat)//2,'old_n':len(walk)//2}
        overlay(img,M,flat,f'c_{id_}.jpg')
        print(kind,id_,round(gain,3),len(walk)//2,'->',len(flat)//2)
    json.dump(res,open('cands.json','w'))
