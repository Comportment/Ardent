package tk.ardentbot.Utils.Updaters;

public class WebsiteDaemon implements Runnable {
    @Override
    public void run() {
        try {
            Runtime.getRuntime().exec("(cd /root/Ardent/web/ && exec npm start)");
        }
        catch (Exception ex) {
        }
    }
}
