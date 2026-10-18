package com.night.soundatlas

import java.util.PriorityQueue
import kotlin.math.*

/** Small deterministic 8-neighbor A* for the overhead acoustic map. */
object AtlasNavigator {
    private data class Next(val node:Int,val score:Float)
    fun traversable(world: World, from: V2, to: V2): Boolean =
        world.walls.none { it.material != Material.OPEN &&
            AcousticSolver.intersects(from,to,it.a,it.b) }

    fun route(world: World, from: V2, to: V2): List<V2> {
        val goal=world.bound(to)
        if(traversable(world,from,goal))return listOf(goal)
        // The grid adapts to the canvas-sized scene and is bounded even for giant worlds.
        val spacing=max(1f,max(world.width,world.height)/110f)
        val cols=ceil(world.width/spacing).toInt()+1
        val rows=ceil(world.height/spacing).toInt()+1
        val total=cols*rows
        if(total>20000)return emptyList()
        fun cell(v:V2):Int {
            val x=(v.x/spacing).roundToInt().coerceIn(0,cols-1)
            val y=(v.y/spacing).roundToInt().coerceIn(0,rows-1)
            return y*cols+x
        }
        fun point(id:Int)=V2((id%cols*spacing).coerceAtMost(world.width),
            (id/cols*spacing).coerceAtMost(world.height))
        val start=cell(from);val finish=cell(goal)
        val g=FloatArray(total){Float.POSITIVE_INFINITY}
        val previous=IntArray(total){-1}
        val closed=BooleanArray(total)
        val queue=PriorityQueue<Next>(compareBy{it.score})
        g[start]=0f;queue.add(Next(start,from.distance(goal)))
        val directions=intArrayOf(-1,0,1)
        var explored=0
        while(queue.isNotEmpty() && explored<total) {
            val current=queue.poll().node
            if(closed[current])continue
            closed[current]=true;explored++
            if(current==finish)break
            val x=current%cols;val y=current/cols
            for(dx in directions)for(dy in directions) {
                if(dx==0&&dy==0)continue
                val xx=x+dx;val yy=y+dy
                if(xx<0||yy<0||xx>=cols||yy>=rows)continue
                val neighbor=yy*cols+xx
                if(closed[neighbor])continue
                if(!traversable(world,point(current),point(neighbor)))continue
                val newCost=g[current]+hypot(dx.toFloat(),dy.toFloat())*spacing
                if(newCost>=g[neighbor])continue
                g[neighbor]=newCost;previous[neighbor]=current
                queue.add(Next(neighbor,newCost+point(neighbor).distance(goal)))
            }
        }
        if(!closed[finish])return emptyList()
        val raw=mutableListOf<V2>()
        var node=finish
        var safe=0
        while(node!=start && safe<total) {
            raw+=point(node)
            node=previous[node]
            if(node<0)return emptyList()
            safe++
        }
        raw.reverse()
        if(raw.isEmpty())return if(traversable(world,from,goal))listOf(goal) else emptyList()
        // Include the exact listener start: grid snapping must never teleport through a wall.
        val steps=mutableListOf<V2>()
        var cursor=from
        var index=0
        while(index<raw.size) {
            var far=index
            for(i in index until raw.size) {
                if(traversable(world,cursor,raw[i]))far=i
                else break
            }
            val waypoint=raw[far]
            // Guard against poorly snapped starting cells on narrow entrances.
            if(!traversable(world,cursor,waypoint))return emptyList()
            steps+=waypoint
            cursor=waypoint
            index=far+1
        }
        if(traversable(world,cursor,goal))steps+=goal
        return steps
    }
}
