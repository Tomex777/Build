package com.homira.aod;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
public class DomainTest {
    private ZonedDateTime at(String value){return ZonedDateTime.parse(value+"Z");}
    @Test public void clockFormatting(){Domain.Element e=new Domain.Element();assertEquals("00:05",Domain.clock(e,at("2026-10-01T00:05:09")));e.h24=false;e.zero=false;e.seconds=true;assertTrue(Domain.clock(e,at("2026-10-01T13:05:09")).startsWith("1:05:09"));}
    @Test public void serializationPreservesEveryEdit(){Domain.Theme t=Domain.presets().get(0);Domain.Element e=t.elements.get(0);e.rotation=15;e.opacity=.6f;e.locked=true;e.spacing=3;e.family="Analog";Domain.Theme read=Domain.decode(Domain.encode(t));assertEquals(Domain.encode(t),Domain.encode(read));}
    @Test public void migratesVersionOne(){Domain.Theme t=Domain.decode("{\"schemaVersion\":1,\"name\":\"Old\",\"elements\":[{\"type\":\"Clock\"}]}");assertEquals("Digital",t.elements.get(0).family);assertTrue(Domain.encode(t).contains("\"schemaVersion\":2"));}
    @Test(expected=IllegalArgumentException.class) public void rejectsFutureVersion(){Domain.decode("{\"schemaVersion\":999,\"elements\":[]}");}
    @Test public void boundsAndSnapping(){Domain.Element e=new Domain.Element();e.x=-100;e.y=900;e.w=2000;e.h=-1;Domain.bounds(e);assertTrue(e.x>=12&&e.x+e.w<=348);assertTrue(e.y>=12&&e.y+e.h<=708);assertEquals(80,Domain.snap(83,200,360),0);assertEquals(12,Domain.snap(15,50,360),0);}
    @Test public void burnInMovesTogetherAndWithinBounds(){Domain.Theme t=Domain.presets().get(0);Set<String> seen=new HashSet<>();for(int i=0;i<32;i++){float[] offset=Domain.shift(t,i);seen.add(Arrays.toString(offset));for(Domain.Element e:t.elements){assertTrue(e.x+offset[0]>=0);assertTrue(e.x+e.w+offset[0]<=360);assertTrue(e.y+offset[1]>=0);assertTrue(e.y+e.h+offset[1]<=720);}}assertEquals(8,seen.size());}
    @Test public void scheduleOvernightUsesStartingDay(){Domain.Rule r=new Domain.Rule();r.days=1<<3;assertTrue(r.matches(at("2026-10-01T20:00:00"),false));assertTrue(r.matches(at("2026-10-02T03:00:00"),false));assertFalse(r.matches(at("2026-10-02T08:00:00"),false));}
    @Test public void schedulePriorityAndCharging(){Domain.Rule a=new Domain.Rule(),b=new Domain.Rule();a.id="a";b.id="b";b.priority=2;b.charging=1;assertSame(a,Domain.resolve(Arrays.asList(b,a),at("2026-10-01T20:00:00"),false));assertSame(b,Domain.resolve(Arrays.asList(b,a),at("2026-10-01T20:00:00"),true));b.priority=0;assertSame(a,Domain.resolve(Arrays.asList(b,a),at("2026-10-01T20:00:00"),true));}
    @Test public void undoRedoAndBranching(){Domain.Theme t=Domain.presets().get(0);Domain.History h=new Domain.History();String initial=Domain.encode(t);h.record(t);t.elements.get(0).x+=20;String moved=Domain.encode(t);t=h.undo(t);assertEquals(initial,Domain.encode(t));t=h.redo(t);assertEquals(moved,Domain.encode(t));t=h.undo(t);h.record(t);t.elements.get(0).color=0xff00ffff;assertFalse(h.canRedo());}
    @Test public void duplicateDeleteIndependent(){Domain.Theme t=Domain.presets().get(0);Domain.Element original=t.elements.get(0),copy=Domain.duplicate(t,original);assertNotEquals(copy.id,original.id);copy.text="changed";assertNotEquals(copy.text,original.text);Domain.delete(t,copy.id);assertEquals(3,t.elements.size());}
    @Test(expected=IllegalArgumentException.class) public void unsafeAssetsRejected(){Domain.Theme t=Domain.presets().get(0);t.elements.get(0).asset="../../private";Domain.validate(t);}
    @Test public void boundedUndo(){Domain.Theme t=Domain.presets().get(0);Domain.History h=new Domain.History();for(int i=0;i<100;i++){h.record(t);t.name="Edit "+i;}int count=0;while(h.canUndo()){t=h.undo(t);count++;}assertEquals(60,count);}
}
