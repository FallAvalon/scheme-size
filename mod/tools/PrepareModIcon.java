import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
public class PrepareModIcon {
 public static void main(String[] args)throws Exception{
  var source=ImageIO.read(new File(args[0]));
  for(int size:new int[]{256,64}){
   var icon=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);var graphics=icon.createGraphics();
   graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
   graphics.drawImage(source,0,0,size,size,null);graphics.dispose();
   ImageIO.write(icon,"png",new File(size==256?args[1]:args[2]));
  }
 }
}
