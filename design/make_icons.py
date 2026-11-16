from PIL import Image, ImageDraw, ImageFont
import os

W,H=1600,900
img=Image.new("RGB",(W,H),"#F4F5FA")
d=ImageDraw.Draw(img)

def font(size,bold=False):
    for p in [r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\msyh.ttc"]:
        try: return ImageFont.truetype(p,size)
        except: pass
    return ImageFont.load_default()

F_TITLE=font(54,True); F_SUB=font(26); F_LABEL=font(34,True); F_DESC=font(23); F_SMALL=font(20)

d.text((80,55),"渡口  Dukou",font=F_TITLE,fill="#14162B")
d.text((82,122),"一个把局域网流量送过边界的网关",font=F_SUB,fill="#5B6075")

# card helper
def card(x,y):
    d.rounded_rectangle([x,y,x+380,y+380],radius=84,fill="#FFFFFF",outline="#E4E6F0",width=2)

def icon_bg(x,y,c1,c2=None):
    if c2:
        # simple vertical gradient
        for i in range(380):
            t=i/379
            col=tuple(int(c1[j]*(1-t)+c2[j]*t) for j in range(3))
            d.line([x,y+i,x+379,y+i],fill=col)
        d.rounded_rectangle([x,y,x+379,y+379],radius=84,outline=None)
        # mask corners by overdraw? create mask
        mask=Image.new("L",(380,380),0)
        md=ImageDraw.Draw(mask); md.rounded_rectangle([0,0,379,379],radius=84,fill=255)
        base=img.crop((x,y,x+380,y+380)).convert("RGB")
        grad=Image.new("RGB",(380,380))
        gd=ImageDraw.Draw(grad)
        for i in range(380):
            t=i/379; gd.line([0,i,379,i],fill=tuple(int(c1[j]*(1-t)+c2[j]*t) for j in range(3)))
        base.paste(grad,(0,0),mask)
        img.paste(base,(x,y))
    else:
        d.rounded_rectangle([x,y,x+379,y+379],radius=84,fill=c1)

# A: gate + flow
# reuse coords relative
def A(cx,cy,s=1.0):
    # not used
    pass
icon_bg(80,210,(79,70,229))
# rerender card frame not needed
# flow lines
d=ImageDraw.Draw(img)
cream="#F5F1E8"; mint="#5EEAD4"
ox,oy=80,210
for yy,col,wdt in [(300,"#A5B4FC",10),(345,mint,10),(370,"#A5B4FC",10)]:
    d.line([ox+24,oy+yy,ox+180,oy+yy],fill=col,width=wdt)
# active line through gate to dot
d.line([ox+150,oy+345,ox+300,oy+345],fill=mint,width=10)
d.ellipse([ox+292,oy+332,ox+328,oy+358],fill=mint)
# arch
d.arc([ox+110,oy+235,ox+270,oy+370],start=180,end=360,fill=cream,width=24)
d.line([ox+122,oy+315,ox+122,oy+365],fill=cream,width=24)
d.line([ox+258,oy+315,ox+258,oy+365],fill=cream,width=24)
# small device nodes left
for yy in [290,355]:
    d.ellipse([ox+10,oy+yy-8,ox+26,oy+yy+8],fill=cream)

# B: bridge
icon_bg(610,210,(15,23,42))
ox,oy=610,210
d=ImageDraw.Draw(img)
# deck
d.rounded_rectangle([ox+55,oy+310,ox+325,oy+334],radius=12,fill=cream)
# arches
d.arc([ox+65,oy+225,ox+195,oy+330],start=180,end=360,fill=mint,width=16)
d.arc([ox+185,oy+225,ox+315,oy+330],start=180,end=360,fill=mint,width=16)
# piers
for xx in [ox+120,ox+195,ox+270]:
    d.rectangle([xx-6,oy+310,xx+6,oy+365],fill=cream)
# nodes below
for xx in [ox+100,ox+190,ox+280]:
    d.ellipse([xx-10,oy+350,xx+10,oy+370],fill=cream)

# C: G portal
icon_bg(1140,210,(49,46,129))
ox,oy=1140,210
d=ImageDraw.Draw(img)
# G arc
d.arc([ox+95,oy+105,ox+285,oy+295],start=35,end=325,fill=cream,width=26)
# G bar
d.rounded_rectangle([ox+195,oy+200,ox+290,oy+226],radius=12,fill=cream)
# route enters and exits to dot
d.line([ox+35,oy+200,ox+100,oy+200],fill=mint,width=10)
d.line([ox+270,oy+200,ox+310,oy+200],fill=mint,width=10)
d.ellipse([ox+306,oy+188,ox+334,oy+212],fill=mint)
# small dots
for yy in [160,240]:
    d.ellipse([ox+60,oy+yy-7,ox+74,oy+yy+7],fill="#A5B4FC")

# labels
d=ImageDraw.Draw(img)
labels=[("A  渡口之门","网关 + 分流 + 端口", "#4F46E5"),
        ("B  双桥","转发 + 连接 + 稳定", "#0F172A"),
        ("C  G 门","品牌字母 + 通道", "#312E81")]
for i,(t,sub,col) in enumerate(labels):
    xx=80+i*530
    d.text((xx,650),t,font=F_LABEL,fill=col)
    d.text((xx,700),sub,font=F_DESC,fill="#5B6075")

d.text((80,805),"推荐 A：门是边界，三条流线代表不同设备，薄荷色圆点是按策略选择的出口。",font=F_SMALL,fill="#6B7085")
d.text((80,840),"图形只保留拱门、流线、节点三个元素；48px 下仍可识别，适合自适应图标。",font=F_SMALL,fill="#6B7085")
img.save("design/icon-concepts.png")
print("saved", os.path.abspath("design/icon-concepts.png"))