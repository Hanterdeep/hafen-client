package haven;

import java.applet.*;
import java.net.URL;
import java.util.*;
import java.awt.*;
import java.awt.event.*;

public class HavenApplet extends Applet {
    public static Map<ThreadGroup, HavenApplet> applets = new HashMap<ThreadGroup, HavenApplet>();
    ThreadGroup p;
    HavenPanel h;
    boolean running = false;
    
    private class ErrorPanel extends Canvas implements haven.error.ErrorStatus {
	String status = "";
	boolean ar = false;
	
	public ErrorPanel() {
	    setBackground(Color.BLACK);
	    addMouseListener(new MouseAdapter() {
		    public void mouseClicked(MouseEvent e) {
			if(ar && !running) {
			    HavenApplet.this.remove(ErrorPanel.this);
			    startgame();
			}
		    }
		});
	}
	
	public boolean goterror(Throwable t) {
	    stopgame();
	    setSize(HavenApplet.this.getSize());
	    HavenApplet.this.add(this);
	    repaint();
	    return(true);
	}
	
	public void connecting() {
	    status = "Connecting to error report server...";
	    repaint();
	}
	
	public void sending() {
	    status = "Sending error report...";
	    repaint();
	}
	
	public void done() {
	    status = "Done";
	    ar = true;
	    repaint();
	}
	
	public void senderror(Exception e) {
	    status = "Could not send error report";
	    ar = true;
	    repaint();
	}
	
	public void paint(Graphics g) {
	    g.setColor(getBackground());
	    g.fillRect(0, 0, getWidth(), getHeight());
	    g.setColor(Color.WHITE);
	    FontMetrics m = g.getFontMetrics();
	    int y = 0;
	    g.drawString("An error has occurred.", 0, y + m.getAscent());
	    y += m.getHeight();
	    g.drawString(status, 0, y + m.getAscent());
	    y += m.getHeight();
	    if(ar) {
		g.drawString("Click to restart the game", 0, y + m.getAscent());
		y += m.getHeight();
	    }
	}
    }
    
    public void destroy() {
	stopgame();
    }
    
    public void startgame() {
	if(running)
	    return;
	h = new HavenPanel(800, 600);
	add(h);
	h.init();
	p = new haven.error.ErrorHandler(new ErrorPanel());
	synchronized(applets) {
	    applets.put(p, this);
	}
	Thread main = new Thread(p, new Runnable() {
		public void run() {
		    Thread ui = new Thread(Utils.tg(), h, "Haven UI thread");
		    ui.start();
		    try {
			while(true) {
			    Bootstrap bill = new Bootstrap();
			    if(getParameter("authcookie") != null)
				bill.setinitcookie(Utils.hex2byte(getParameter("authcookie")));
			    bill.setaddr(getCodeBase().getHost());
			    try {
				Resource.addurl(new URL("https", getCodeBase().getHost(), 443, "/res/"));
			    } catch(java.net.MalformedURLException e) {
				throw(new RuntimeException(e));
			    }
			    Session sess = bill.run(h);
			    RemoteUI rui = new RemoteUI(sess);
			    rui.run(h.newui(sess));
			}
		    } catch(InterruptedException e) {
		    } finally {
			ui.interrupt();
		    }
		}
	    });
	main.start();
	running = true;
    }
    
    public void stopgame() {
	if(!running)
	    return;
	running = false;
	synchronized(applets) {
	    applets.remove(p);
	}
	p.interrupt();
	remove(h);
	p = null;
	h = null;
    }
    
    public void init() {
	resize(800, 600);
	startgame();
    }
    
    static {
	WebBrowser.self = new WebBrowser() {
		public void show(URL url) {
		    HavenApplet a;
		    synchronized(applets) {
			a = applets.get(Utils.tg());
		    }
		    if(a != null)
			a.getAppletContext().showDocument(url);
		}
	    };
    }
}
