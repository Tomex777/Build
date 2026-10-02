package com.homira.aod;

/** Offline edge extraction with bounded image dimensions supplied by Surface. */
public final class Outline {
  private Outline() {}
  public static int[] mask(int[] pixels, int width, int height, float detail, float thickness) {
    if (width < 3 || height < 3 || width > 1200 || height > 1200 || pixels.length != width*height)
      throw new IllegalArgumentException("Invalid outline image");
    int[] gray=new int[pixels.length], blurred=new int[pixels.length];
    for(int i=0;i<pixels.length;i++) {
      int c=pixels[i];
      gray[i]=((c>>>16 & 255)*77+(c>>>8 & 255)*150+(c & 255)*29)>>>8;
    }
    for(int y=1;y<height-1;y++) for(int x=1;x<width-1;x++) {
      int i=y*width+x;
      blurred[i]=(gray[i]*4+(gray[i-1]+gray[i+1]+gray[i-width]+gray[i+width])*2
          +gray[i-width-1]+gray[i-width+1]+gray[i+width-1]+gray[i+width+1])/16;
    }
    int[] alpha=new int[pixels.length];
    float threshold=220-Math.max(0,Math.min(1,detail))*195;
    for(int y=2;y<height-2;y++) for(int x=2;x<width-2;x++) {
      int i=y*width+x;
      int gx=-blurred[i-width-1]+blurred[i-width+1]-2*blurred[i-1]+2*blurred[i+1]-blurred[i+width-1]+blurred[i+width+1];
      int gy=-blurred[i-width-1]-2*blurred[i-width]-blurred[i-width+1]+blurred[i+width-1]+2*blurred[i+width]+blurred[i+width+1];
      alpha[i]=Math.min(255,Math.max(0,Math.round(((float)Math.hypot(gx,gy)-threshold)*2)));
    }
    int radius=Math.min(3,Math.max(0,Math.round(thickness/2-.5f)));
    int[] result=new int[pixels.length];
    for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
      int a=alpha[y*width+x];
      for(int dy=-radius;dy<=radius;dy++) for(int dx=-radius;dx<=radius;dx++)
        if(x+dx>=0 && x+dx<width && y+dy>=0 && y+dy<height) a=Math.max(a,alpha[(y+dy)*width+x+dx]);
      result[y*width+x]=a<<24|0xffffff;
    }
    return result;
  }
}
