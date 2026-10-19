"""Offline isometric preview of the delivered cuboid meshes; approximate vanilla texture colors, no server needed."""
from pathlib import Path
import json,math,random
import numpy as np
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1]
PALETTE={'stone':(128,128,123),'trim':(144,146,141),'dark':(58,61,64),'wood':(165,132,77),
         'log':(111,87,50),'rings':(184,153,100),'leaves':(91,117,49),'iron':(176,137,109),
         'smooth':(159,161,157),'metal':(209,210,205),'lamp':(110,165,143),
         'furnace':(103,106,105),'mouth':(45,48,47),'coal':(34,37,38)}
font_path='C:/Windows/Fonts/msyh.ttc'
font=lambda size:ImageFont.truetype(font_path,size)
canvas=Image.new('RGB',(1440,620),'#F0EEE7');draw=ImageDraw.Draw(canvas)
draw.text((42,26),'Genlite · 简洁生成器',font=font(30),fill='#273735')
draw.text((42,73),'交付 JSON 模型的结构预览 · 原版材质取色示意，非客户端截图',font=font(17),fill='#66706C')

def render(model):
    size=420;rgba=np.zeros((size,size,4),dtype=np.uint8);depth=np.full((size,size),-1e9)
    def project(v):
        x,y,z=v;return np.array([210+(x-z)*10.8,300+(x+z-16)*5.4-y*11.8,x+z+y*.9])
    def triangle(points,cols):
        a,b,c=points;minx=max(0,int(min(a[0],b[0],c[0])));maxx=min(size-1,int(max(a[0],b[0],c[0]))+1)
        miny=max(0,int(min(a[1],b[1],c[1])));maxy=min(size-1,int(max(a[1],b[1],c[1]))+1)
        if minx>maxx or miny>maxy:return
        xx,yy=np.meshgrid(np.arange(minx,maxx+1)+.5,np.arange(miny,maxy+1)+.5)
        den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-8:return
        u=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
        v=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w=1-u-v
        zz=u*a[2]+v*b[2]+w*c[2];region=depth[miny:maxy+1,minx:maxx+1]
        mask=(u>=-.001)&(v>=-.001)&(w>=-.001)&(zz>=region)
        region[mask]=zz[mask];rgba[miny:maxy+1,minx:maxx+1][mask]=[*cols,255]
    for n,part in enumerate(model['elements']):
        x,y,z=part['from'];X,Y,Z=part['to']
        faces={'up':([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],1.12),
               'south':([(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],.78),
               'east':([(X,y,z),(X,y,Z),(X,Y,Z),(X,Y,z)],.94)}
        for side,(corners,shade) in faces.items():
            texture=part['faces'][side]['texture'][1:];base=PALETTE.get(texture,(130,130,130))
            a,b,c,d=[np.array(v) for v in corners];width=max(1,round(np.linalg.norm(b-a)));height=max(1,round(np.linalg.norm(d-a)))
            for i in range(width):
                for j in range(height):
                    corners3=[a+(b-a)*u+(d-a)*v for u,v in [(i/width,j/height),((i+1)/width,j/height),((i+1)/width,(j+1)/height),(i/width,(j+1)/height)]]
                    color_noise=random.Random(f'{texture}:{i}:{j}').uniform(.90,1.08)
                    if texture=='stone' and (i%5==0 or j%5==0):color_noise*=.8
                    if texture=='rings' and abs(max(abs(i-width/2),abs(j-height/2))%3)<.6:color_noise*=.79
                    if texture=='log' and i%3==0:color_noise*=.78
                    col=tuple(min(255,round(v*shade*color_noise)) for v in base);p=list(map(project,corners3))
                    triangle([p[0],p[1],p[2]],col);triangle([p[0],p[2],p[3]],col)
    return Image.fromarray(rgba)

for index,(mid,label,desc) in enumerate([('cobblestone_generator','原石生成器','石台 / 原石簇'),('timber_generator','木材生成器','木座 / 原木树桩'),('iron_generator','铁生成器','石座 / 铁矿簇')]):
    left=28+index*474
    draw.rounded_rectangle((left,117,left+440,586),radius=20,fill='#FCFBF7')
    model=json.loads((ROOT/'craftengine/genlite/resourcepack/assets/genlite/models/block'/f'{mid}_running.json').read_text())
    image=render(model);canvas.paste(image,(left+10,128),image)
    draw.text((left+25,500),label,font=font(24),fill='#293C36');draw.text((left+25,542),desc,font=font(17),fill='#78817C')
path=ROOT/'art/generator-models-preview.png';path.parent.mkdir(exist_ok=True);canvas.save(path);print(path)
