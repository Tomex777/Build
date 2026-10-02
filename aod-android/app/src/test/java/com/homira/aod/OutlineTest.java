package com.homira.aod;
import static org.junit.Assert.*;
import java.time.ZonedDateTime;
import java.util.Arrays;
import org.junit.Test;
public class OutlineTest {
  private int visible(int[] pixels) { int count=0; for(int p:pixels) if((p>>>24)>0) count++; return count; }
  @Test public void flatImageProducesNoEdges() {
    int[] pixels=new int[1600]; Arrays.fill(pixels,0xff808080);
    assertEquals(0,visible(Outline.mask(pixels,40,40,1,1)));
  }
  @Test public void detailAndThicknessChangeSparseEdges() {
    int[] pixels=new int[6400]; Arrays.fill(pixels,0xff000000);
    for(int y=20;y<60;y++) for(int x=20;x<60;x++) pixels[y*80+x]=0xff303030;
    int simple=visible(Outline.mask(pixels,80,80,0,1));
    int detailed=visible(Outline.mask(pixels,80,80,1,1));
    assertTrue(detailed>simple); assertTrue(detailed<pixels.length/4);
    assertTrue(visible(Outline.mask(pixels,80,80,1,6))>detailed);
  }
  @Test public void replacementPreservesOutlineEditsAndClock() {
    Domain.Theme t=Domain.presets().get(0);
    Domain.Element e=Domain.installWallpaper(t,"a".repeat(64)+".png");
    e.lineWidth=3; e.outlineDetail=.72f; e.color=0xffabcdef;
    Domain.installWallpaper(t,"b".repeat(64)+".png");
    assertEquals(4,t.elements.size()); assertSame(e,t.elements.get(0));
    assertEquals("Clock",t.elements.get(1).type);
    Domain.Theme read=Domain.decode(Domain.encode(t));
    assertEquals(.72f,read.elements.get(0).outlineDetail,.001);
    assertEquals(3,read.elements.get(0).lineWidth,0);
    assertEquals(0xffabcdef,read.elements.get(0).color);
    assertEquals(0xff000000,read.background);
  }
  @Test public void independentClockPartsAndDialSettingsSurviveSave() {
    Domain.Element e=new Domain.Element(); ZonedDateTime t=ZonedDateTime.parse("2026-10-02T13:07:29Z");
    e.family="Hours"; assertEquals("13",Domain.clock(e,t));
    e.h24=false; e.zero=false; assertEquals("1",Domain.clock(e,t));
    e.family="Minutes"; assertEquals("07",Domain.clock(e,t));
    e.family="Seconds"; assertEquals("29",Domain.clock(e,t));
    e.dialRing=false; e.dialMarkers=false;
    Domain.Theme theme=new Domain.Theme(); theme.clockPreset=true; theme.name="Renamed clock"; theme.elements.add(e);
    Domain.Element read=Domain.decode(Domain.encode(theme)).elements.get(0);
    assertFalse(read.dialRing); assertFalse(read.dialMarkers);
    assertTrue(Domain.decode(Domain.encode(theme)).clockPreset);
  }
}
