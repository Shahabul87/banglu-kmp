import subprocess, re, sys, time, xml.etree.ElementTree as ET
def sh(*a): return subprocess.run(["adb","shell",*a],capture_output=True,text=True).stdout
def dump():
    sh("uiautomator","dump","--windows","/sdcard/kb.xml"); x=sh("cat","/sdcard/kb.xml")
    return ET.fromstring(x)
def keys(tree=None):
    t=tree or dump(); out=[]
    for n in t.iter('node'):
        if n.get('package')=='com.banglu.keyboard':
            cd=n.get('content-desc') or ''; tx=n.get('text') or ''
            if (cd or tx) and n.get('bounds'):
                b=[int(v) for v in re.findall(r'\d+',n.get('bounds'))]
                out.append((cd,tx,b,n.get('clickable')))
    return out
def center(b): return ((b[0]+b[2])//2,(b[1]+b[3])//2)
def find(label,tree=None):
    for cd,tx,b,c in keys(tree):
        if cd==label or tx==label: return b
    for cd,tx,b,c in keys(tree):
        if label in cd or label in tx: return b
    return None
def tap(label,tree=None):
    b=find(label,tree)
    if not b: raise RuntimeError(f"key not found: {label}")
    x,y=center(b); sh("input","tap",str(x),str(y)); return (x,y)
def field_text():
    t=dump()
    for n in t.iter('node'):
        if n.get('class','').endswith('EditText'): return n.get('text')
    return None
