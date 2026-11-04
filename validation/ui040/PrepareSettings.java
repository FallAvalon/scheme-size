package probe;

/** Writes only the newly created test profile, before launching the game. */
public final class PrepareSettings {
    public static void main(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("test data directory and UI scale required");
        int scale=Integer.parseInt(args[1]);
        if(scale<50||scale>300)throw new IllegalArgumentException("UI scale");
        arc.Settings settings=new arc.Settings();
        settings.setDataDirectory(new arc.files.Fi(args[0]));settings.load();
        settings.put("uiscale",scale);settings.put("locale","ru");settings.forceSave();
        System.out.println("Prepared isolated UI scale "+scale);
    }
}
