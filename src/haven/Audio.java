package haven;

import java.util.*;
import java.io.InputStream;
import javax.sound.sampled.*;

public class Audio {
    public static boolean enabled = true;
    private static Thread player;
    public static final AudioFormat fmt = new AudioFormat(44100, 16, 2, true, false);
    private static Collection<CS> ncl = new LinkedList<CS>();
    private static Object queuemon = new Object();
    private static Collection<Runnable> queue = new LinkedList<Runnable>();
    private static int bufsize = 32768;
    
    public interface CS {
	public boolean get(double[] sample);
    }
    
    public static class DataClip implements CS {
	private InputStream clip;
	private double vol, sp;
	private int ack = 0;
	private double[] ov = new double[2];
	public boolean eof;

	public DataClip(InputStream clip, double vol, double sp) {
	    this.clip = clip;
	    this.vol = vol;
	    this.sp = sp;
	}
	
	public DataClip(InputStream clip) {
	    this(clip, 1.0, 1.0);
	}
	
	public void finwait() throws InterruptedException {
	    synchronized(this) {
		if(eof)
		    return;
		wait();
	    }
	}
	
	public boolean get(double[] sm) {
	    try {
		ack += 44100.0 * sp;
		while(ack >= 44100) {
		    for(int i = 0; i < 2; i++) {
			int b1 = clip.read();
			int b2 = clip.read();
			if((b1 < 0) || (b2 < 0)) {
			    synchronized(this) {
				eof = true;
				notifyAll();
			    }
			    return(false);
			}
			int v = b1 + (b2 << 8);
			if(v >= 32768)
			    v -= 65536;
			ov[i] = ((double)v / 32768.0) * vol;
		    }
		    ack -= 44100;
		}
	    } catch(java.io.IOException e) {
		synchronized(this) {
		    eof = true;
		    notifyAll();
		}
		return(false);
	    }
	    for(int i = 0; i < 2; i++)
		sm[i] = ov[i];
	    return(true);
	}
    }
	
    private static class Player extends Thread {
	private Collection<CS> clips = new LinkedList<CS>();
	private int srate, nch = 2;
	
	Player() {
	    super(Utils.tg(), "Haven audio player");
	    setDaemon(true);
	    srate = (int)fmt.getSampleRate();
	}
	
	private void fillbuf(byte[] buf, int off, int len) {
	    double[] val = new double[nch];
	    double[] sm = new double[nch];
	    while(len > 0) {
		for(int i = 0; i < nch; i++)
		    val[i] = 0;
		for(Iterator<CS> i = clips.iterator(); i.hasNext();) {
		    CS cs = i.next();
		    if(!cs.get(sm)) {
			i.remove();
			continue;
		    }
		    for(int ch = 0; ch < nch; ch++)
			val[ch] += sm[ch];
		}
		for(int i = 0; i < nch; i++) {
		    int iv = (int)(val[i] * 32767.0);
		    if(iv < 0) {
			if(iv < -32768)
			    iv = -32768;
			iv += 65536;
		    } else {
			if(iv > 32767)
			    iv = 32767;
		    }
		    buf[off++] = (byte)(iv & 0xff);
		    buf[off++] = (byte)((iv & 0xff00) >> 8);
		    len -= 2;
		}
	    }
	}

	public void run() {
	    SourceDataLine line = null;
	    try {
		try {
		    line = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
		    line.open(fmt, bufsize);
		    line.start();
		} catch(Exception e) {
		    e.printStackTrace();
		    return;
		}
		int bufsize = line.getBufferSize();
		System.out.println(bufsize);
		byte[] buf = new byte[1024];
		while(true) {
		    if(Thread.interrupted())
			throw(new InterruptedException());
		    synchronized(queuemon) {
			Collection<Runnable> queue = Audio.queue;
			Audio.queue = new LinkedList<Runnable>();
			for(Runnable r : queue)
			    r.run();
		    }
		    synchronized(ncl) {
			for(CS cs : ncl)
			    clips.add(cs);
			ncl.clear();
		    }
		    /*
		    while(true) {
			int bufdata = bufsize - line.available();
			if(bufdata < 1024)
			    break;
			Thread.sleep(((bufdata - 1024) / 4) / 44);
		    }
		    */
		    fillbuf(buf, 0, 1024);
		    for(int off = 0; off < buf.length; off += line.write(buf, off, buf.length - off));
		}
	    } catch(InterruptedException e) {
	    } finally {
		synchronized(Audio.class) {
		    player = null;
		}
		if(line != null)
		    line.close();
	    }
	}
    }

    private static synchronized void ckpl() {
	if(enabled) {
	    if(player == null) {
		player = new Player();
		player.start();
	    }
	} else {
	    ncl.clear();
	}
    }
    
    public static void play(CS clip) {
	synchronized(ncl) {
	    ncl.add(clip);
	}
	ckpl();
    }
    
    public static void play(final InputStream clip, final double vol, final double sp) {
	play(new DataClip(clip, vol, sp));
    }

    public static void play(byte[] clip, double vol, double sp) {
	play(new DataClip(new java.io.ByteArrayInputStream(clip), vol, sp));
    }
    
    public static void play(byte[] clip) {
	play(clip, 1.0, 1.0);
    }
    
    public static void queue(Runnable d) {
	synchronized(queuemon) {
	    queue.add(d);
	}
	ckpl();
    }

    public static void play(final Resource clip) {
	queue(new Runnable() {
		public void run() {
		    if(clip.loading)
			queue.add(this);
		    else
			play(clip.layer(Resource.audio).clip);
		}
	    });
    }
    
    public static void play(final Indir<Resource> clip) {
	queue(new Runnable() {
		public void run() {
		    Resource r = clip.get();
		    if(r == null)
			queue.add(this);
		    else
			play(r.layer(Resource.audio).clip);
		}
	    });
    }
    
    public static byte[] readclip(InputStream in) throws java.io.IOException {
	AudioInputStream cs;
	try {
	    cs = AudioSystem.getAudioInputStream(fmt, AudioSystem.getAudioInputStream(in));
	} catch(UnsupportedAudioFileException e) {
	    throw(new java.io.IOException("Unsupported audio encoding"));
	}
	java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
	byte[] bbuf = new byte[65536];
	while(true) {
	    int rv = cs.read(bbuf);
	    if(rv < 0)
		break;
	    buf.write(bbuf, 0, rv);
	}
	return(buf.toByteArray());
    }
    
    public static void main(String[] args) throws Exception {
	Collection<DataClip> clips = new LinkedList<DataClip>();
	for(int i = 0; i < args.length; i++) {
	    if(args[i].equals("-b")) {
		bufsize = Integer.parseInt(args[++i]);
	    } else {
		DataClip c = new DataClip(new java.io.FileInputStream(args[i]));
		clips.add(c);
	    }
	}
	for(DataClip c : clips)
	    play(c);
	for(DataClip c : clips)
	    c.finwait();
    }
}
