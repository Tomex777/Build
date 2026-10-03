#!/usr/bin/env python3
"""Generate original Mise starter actors with real rigid-part skinning, in meters.

No downloaded geometry or generator dependencies. Rebuilding produces identical GLBs.
Vehicle meshes are weighted to named joints with inverse rest transforms, so steering,
wheel rotation and doors use the same durable pose pipeline as humanoid bones.
"""
import argparse
import json
import math
import struct
from pathlib import Path


def sub(a, b): return tuple(x - y for x, y in zip(a, b))
def add(a, b): return tuple(x + y for x, y in zip(a, b))
def mul(a, s): return tuple(x * s for x in a)
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def unit(a): return mul(a, 1 / max(math.sqrt(sum(x*x for x in a)), 1e-12))


class Model:
    def __init__(self, name):
        self.name, self.parts = name, {}
        self.joints = [("Body", None, (0, 0, 0))]
        self.colors = [("Paint", (.12, .38, .42), .35), ("Rubber", (.025, .03, .035), .05),
                       ("Metal", (.55, .60, .64), .85), ("Glass", (.10, .19, .24), .35),
                       ("Light", (.93, .89, .70), .15), ("Tail light", (.55, .025, .03), .1),
                       ("Bark", (.24, .12, .055), 0), ("Leaves", (.12, .28, .10), 0),
                       ("Fresh leaves", (.21, .38, .13), 0)]

    def joint(self, name, center, parent=0):
        self.joints.append((name, parent, center))
        return len(self.joints)-1

    def triangle(self, points, material=0, joint=0, normals=None):
        p, n, ids = self.parts.setdefault((material, joint), ([], [], []))
        normal = unit(cross(sub(points[1], points[0]), sub(points[2], points[0])))
        ids.extend(range(len(p), len(p)+3)); p.extend(points)
        n.extend(normals or [normal]*3)

    def box(self, c, size, material=0, joint=0):
        v = [add(c, (x*size[0]/2, y*size[1]/2, z*size[2]/2))
             for x, y, z in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),
                             (-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
        for a,b,c,d in [(0,3,2,1),(4,5,6,7),(0,4,7,3),(1,2,6,5),(0,1,5,4),(3,7,6,2)]:
            self.triangle([v[a],v[b],v[c]],material,joint)
            self.triangle([v[a],v[c],v[d]],material,joint)

    def tube(self, a, b, radius, material=2, joint=0, sides=12):
        w = unit(sub(b, a)); u = unit(cross(w, (0,1,0) if abs(w[1]) < .9 else (1,0,0))); v = cross(w,u)
        for i in range(sides):
            normal = [add(mul(u, math.cos(t)),mul(v,math.sin(t))) for t in [2*math.pi*i/sides,2*math.pi*(i+1)/sides]]
            p,q = [add(a,mul(n,radius)) for n in normal]; r,s = [add(b,mul(n,radius)) for n in normal]
            self.triangle([p,s,r],material,joint,[normal[0],normal[1],normal[0]])
            self.triangle([p,q,s],material,joint,[normal[0],normal[1],normal[1]])
            self.triangle([a,q,p],material,joint); self.triangle([b,r,s],material,joint)

    def sphere(self, center, radius, material=7, joint=0):
        for iy in range(10):
            for ix in range(16):
                ns = [(math.sin(t)*math.cos(p),math.cos(t),math.sin(t)*math.sin(p))
                      for t,p in [(math.pi*iy/10,math.tau*ix/16),(math.pi*(iy+1)/10,math.tau*ix/16),
                                  (math.pi*(iy+1)/10,math.tau*(ix+1)/16),(math.pi*iy/10,math.tau*(ix+1)/16)]]
                ps = [add(center,tuple(n[a]*radius[a] for a in range(3))) for n in ns]
                for tri in [(0,2,1),(0,3,2)]: self.triangle([ps[i] for i in tri],material,joint,[ns[i] for i in tri])

    def ring(self, center, radius, thickness, material=1, joint=0, width=None):
        # Axle along X; normals and tire tread stay attached to the rotating joint.
        for i in range(48):
            for k in range(10):
                ns, ps = [], []
                for u,v in [(i,k),(i+1,k),(i+1,k+1),(i,k+1)]:
                    t,p = math.tau*u/48, math.tau*v/10
                    ns.append((math.sin(p),math.cos(p)*math.cos(t),math.cos(p)*math.sin(t)))
                    ps.append(add(center,((width or thickness)*math.sin(p),(radius+thickness*math.cos(p))*math.cos(t),(radius+thickness*math.cos(p))*math.sin(t))))
                for tri in [(0,1,2),(0,2,3)]: self.triangle([ps[a] for a in tri],material,joint,[ns[a] for a in tri])

    def wheel(self, center, radius, thickness, joint, car=False):
        self.ring(center,radius,thickness,1,joint,width=.105 if car else None)
        self.ring(center,radius-thickness*1.1,thickness*.25,2,joint)
        for i in range(12 if not car else 6):
            t = math.tau*i/(12 if not car else 6)
            rim = add(center,(0,(radius-thickness)*math.cos(t),(radius-thickness)*math.sin(t)))
            self.tube(center,rim,.007 if not car else .025,2,joint,6)
        self.tube(add(center,(-.08,0,0)),add(center,(.08,0,0)),.045,2,joint)

    def write(self, directory):
        binary, views, accessors = bytearray(), [], []
        def accessor(values, component, shape, bounds=False):
            count = len(values); flat = [v for row in values for v in row] if shape != "SCALAR" else values
            binary.extend(b'\0' * (-len(binary)%4)); offset = len(binary)
            binary.extend(struct.pack('<'+{5126:'f',5123:'H',5125:'I'}[component]*len(flat),*flat))
            views.append(dict(buffer=0,byteOffset=offset,byteLength=len(binary)-offset))
            value=dict(bufferView=len(views)-1,componentType=component,count=count,type=shape)
            if bounds:
                value.update(min=[min(row[a] for row in values) for a in range(3)],max=[max(row[a] for row in values) for a in range(3)])
            accessors.append(value); return len(accessors)-1
        primitives=[]
        for (material,joint),(p,n,indices) in self.parts.items():
            primitives.append(dict(attributes=dict(POSITION=accessor(p,5126,'VEC3',True),NORMAL=accessor(n,5126,'VEC3'),
                                    JOINTS_0=accessor([[joint,0,0,0]]*len(p),5123,'VEC4'),WEIGHTS_0=accessor([[1.,0.,0.,0.]]*len(p),5126,'VEC4')),
                                   indices=accessor(indices,5125,'SCALAR'),material=material))
        nodes=[dict(name=self.name,children=[1,len(self.joints)+1])]
        for i,(name,parent,center) in enumerate(self.joints):
            node=dict(name=name,translation=list(sub(center,self.joints[parent][2]) if parent is not None else center))
            children=[j+1 for j,(_,p,_) in enumerate(self.joints) if p==i]
            if children: node['children']=children
            nodes.append(node)
        nodes.append(dict(name=self.name+' mesh',mesh=0,skin=0))
        matrices=[]
        for _,_,(x,y,z) in self.joints: matrices.append([1,0,0,0,0,1,0,0,0,0,1,0,-x,-y,-z,1])
        inverse=accessor(matrices,5126,'MAT4')
        data=dict(asset=dict(version='2.0',generator='Mise original actor generator 1'),scene=0,scenes=[dict(nodes=[0])],nodes=nodes,
                  meshes=[dict(name=self.name,primitives=primitives)],skins=[dict(name='Mechanical parts',joints=list(range(1,len(self.joints)+1)),skeleton=1,inverseBindMatrices=inverse)],
                  materials=[dict(name=name,pbrMetallicRoughness=dict(baseColorFactor=[*rgb,1],metallicFactor=metal,roughnessFactor=.65 if metal<.5 else .28)) for name,rgb,metal in self.colors],
                  buffers=[dict(byteLength=len(binary))],bufferViews=views,accessors=accessors)
        encoded=json.dumps(data,separators=(',',':')).encode(); encoded+=b' '*(-len(encoded)%4); binary+=b'\0'*(-len(binary)%4)
        payload=struct.pack('<III',0x46546c67,2,12+8+len(encoded)+8+len(binary))+struct.pack('<II',len(encoded),0x4e4f534a)+encoded+struct.pack('<II',len(binary),0x004e4942)+binary
        path=directory/('mise_'+self.name.lower()+'.glb'); path.write_bytes(payload)
        print(f'{path.name}: {len(payload)} bytes, {len(self.joints)} joints, {sum(len(p) for p,_,_ in self.parts.values())//3} triangles')


def bicycle():
    m=Model('Bicycle'); rear=(0,.34,-.57); front=(0,.34,.57)
    steer=m.joint('Front steering',(0,.75,.43)); fw=m.joint('Front wheel',front,steer); rw=m.joint('Rear wheel',rear); crank=m.joint('Pedals',(0,.34,-.06))
    m.wheel(front,.30,.035,fw); m.wheel(rear,.30,.035,rw)
    seat=(0,.82,-.30); head=(0,.84,.40); bottom=(0,.34,-.06)
    for a,b in [(rear,seat),(seat,bottom),(bottom,rear),(seat,head),(head,bottom)]: m.tube(a,b,.024,0)
    m.tube(head,(0,.64,.44),.032,0,steer)
    for x in [-.065,.065]: m.tube((x,.66,.44),(x,.34,.57),.017,2,steer)
    m.tube((0,.82,-.30),(0,.97,-.34),.017,2); m.box((0,.98,-.34),(.19,.045,.26),1)
    m.tube(head,(0,1.01,.42),.018,2,steer); m.tube((-.24,1.01,.42),(.24,1.01,.42),.018,2,steer)
    for x in [-.20,.20]: m.box((x,1.01,.42),(.10,.04,.045),1,steer)
    for x,y in [(-.08,.18),(.08,.50)]:
        m.tube((x,.34,-.06),(x,y,-.06),.014,2,crank); m.box((x,y,-.06),(.12,.03,.09),1,crank)
    # Closed chain loop with two sprockets, attached to the static rear assembly.
    for y in [.32,.39]: m.tube((-.075,y,-.57),(-.075,y,-.06),.007,1)
    return m


def car():
    m=Model('Car'); m.colors[0]=('Paint',(.32,.12,.09),.45)
    m.box((0,.47,0),(1.65,.42,3.70)); m.box((0,.77,-.25),(1.50,.32,2.6))
    m.box((0,1.04,-.25),(1.33,.35,1.6),3); m.box((0,1.26,-.25),(1.48,.08,1.75))
    m.box((0,.83,1.25),(1.52,.14,1.12)); m.box((0,.83,-1.54),(1.52,.14,.62))
    for side in [-1,1]:
        for z,label in [(1.13,'Front'),(-1.13,'Rear')]:
            center=(side*.81,.33,z)
            steer=m.joint(label+' '+('left' if side<0 else 'right')+' steering',center) if label=='Front' else 0
            wheel=m.joint(label+' '+('left' if side<0 else 'right')+' wheel',center,steer)
            m.wheel(center,.255,.067,wheel,True)
        door=m.joint(('Left' if side<0 else 'Right')+' door',(side*.765,.68,.54))
        m.box((side*.765,.72,-.04),(.045,.45,1.12),0,door)
        m.box((side*.746,1.05,-.04),(.045,.30,1.05),3,door)
        m.box((side*.80,.84,-.36),(.035,.035,.14),2,door)
        m.box((side*.91,1.00,.46),(.20,.09,.14),0,door)
        m.box((side*.57,.67,1.87),(.37,.14,.03),4)
        m.box((side*.59,.68,-1.87),(.35,.14,.03),5)
    for z in [-1.89,1.89]: m.box((0,.38,z),(1.52,.10,.09),2)
    m.box((0,.55,1.90),(.54,.13,.02),1)
    return m


def tree():
    m=Model('Tree'); m.tube((0,0,0),(.08,2.20,0),.15,6,sides=16)
    for i,(x,y,z) in enumerate([(-.65,1.8,.1),(.65,2.1,-.3),(.05,2.7,.25),(-.40,2.50,-.35),(.50,2.5,.45)]):
        m.tube((.04,y-.75,0),(x,y,z),.065,6)
        m.sphere((x,y+.3,z),(.67,.65,.65),7+i%2)
    for x,z in [(.28,.1),(-.22,.16),(.05,-.28)]: m.tube((0,.18,0),(x,.03,z),.065,6)
    return m


if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--output-dir',type=Path,default=Path(__file__).resolve().parents[1]/'app/src/main/assets/models')
    output=parser.parse_args().output_dir; output.mkdir(parents=True,exist_ok=True)
    for model in [bicycle(),car(),tree()]: model.write(output)
