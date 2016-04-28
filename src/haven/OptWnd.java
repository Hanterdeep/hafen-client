/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;


import java.util.LinkedList;
import java.util.Locale;

public class OptWnd extends Window {
    public static final Coord PANEL_POS = new Coord(220, 30);
    public static final Coord Q_TYPE_PADDING = new Coord(3, 0);
    public final Panel main, video, audio;
    private final Panel display, general, camera, radar;
    public Panel current;

    public void chpanel(Panel p) {
	if(current != null)
	    current.hide();
	(current = p).show();
	pack();
    }

    public class PButton extends Button {
	public final Panel tgt;
	public final int key;

	public PButton(int w, String title, int key, Panel tgt) {
	    super(w, title);
	    this.tgt = tgt;
	    this.key = key;
	}

	public void click() {
	    chpanel(tgt);
	}

	public boolean type(char key, java.awt.event.KeyEvent ev) {
	    if((this.key != -1) && (key == this.key)) {
		click();
		return (true);
	    }
	    return (false);
	}
    }

    public class Panel extends Widget {
	public Panel() {
	    visible = false;
	    c = Coord.z;
	}
    }

    public class VideoPanel extends Panel {
	public VideoPanel(Panel back) {
	    super();
	    add(new PButton(200, "Back", 27, back), new Coord(0, 180));
	    pack();
	}

	public class CPanel extends Widget {
	    public final GLSettings cf;

	    public CPanel(GLSettings gcf) {
		this.cf = gcf;
		int y = 0;
		add(new CheckBox("Per-fragment lighting") {
		    {
			a = cf.flight.val;
		    }

		    public void set(boolean val) {
			if(val) {
			    try {
				cf.flight.set(true);
			    } catch(GLSettings.SettingException e) {
				getparent(GameUI.class).error(e.getMessage());
				return;
			    }
			} else {
			    cf.flight.set(false);
			}
			a = val;
			cf.dirty = true;
		    }
		}, new Coord(0, y));
		y += 25;
		add(new CheckBox("Render shadows") {
		    {
			a = cf.lshadow.val;
		    }

		    public void set(boolean val) {
			if(val) {
			    try {
				cf.lshadow.set(true);
			    } catch(GLSettings.SettingException e) {
				getparent(GameUI.class).error(e.getMessage());
				return;
			    }
			} else {
			    cf.lshadow.set(false);
			}
			a = val;
			cf.dirty = true;
		    }
		}, new Coord(0, y));
		y += 25;
		add(new CheckBox("Antialiasing") {
		    {
			a = cf.fsaa.val;
		    }

		    public void set(boolean val) {
			try {
			    cf.fsaa.set(val);
			} catch(GLSettings.SettingException e) {
			    getparent(GameUI.class).error(e.getMessage());
			    return;
			}
			a = val;
			cf.dirty = true;
		    }
		}, new Coord(0, y));
		y += 25;
		add(new Label("Anisotropic filtering"), new Coord(0, y));
		if(cf.anisotex.max() <= 1) {
		    add(new Label("(Not supported)"), new Coord(15, y + 15));
		} else {
		    final Label dpy = add(new Label(""), new Coord(165, y + 15));
		    add(new HSlider(160, (int) (cf.anisotex.min() * 2), (int) (cf.anisotex.max() * 2), (int) (cf.anisotex.val * 2)) {
			protected void added() {
			    dpy();
			    this.c.y = dpy.c.y + ((dpy.sz.y - this.sz.y) / 2);
			}

			void dpy() {
			    if(val < 2)
				dpy.settext("Off");
			    else
				dpy.settext(String.format("%.1f\u00d7", (val / 2.0)));
			}

			public void changed() {
			    try {
				cf.anisotex.set(val / 2.0f);
			    } catch(GLSettings.SettingException e) {
				getparent(GameUI.class).error(e.getMessage());
				return;
			    }
			    dpy();
			    cf.dirty = true;
			}
		    }, new Coord(0, y + 15));
		}
		y += 35;
		add(new Button(200, "Reset to defaults") {
		    public void click() {
			cf.cfg.resetprefs();
			curcf.destroy();
			curcf = null;
		    }
		}, new Coord(0, 150));
		pack();
	    }
	}

	private CPanel curcf = null;

	public void draw(GOut g) {
	    if((curcf == null) || (g.gc.pref != curcf.cf)) {
		if(curcf != null)
		    curcf.destroy();
		curcf = add(new CPanel(g.gc.pref), Coord.z);
	    }
	    super.draw(g);
	}
    }

    public OptWnd(boolean gopts) {
	super(Coord.z, "Options", true);
	main = add(new Panel());
	video = add(new VideoPanel(main));
	audio = add(new Panel());
	display = add(new Panel());
	general = add(new Panel());
	camera = add(new Panel());
	radar = add(new Panel());
	int y;

	addPanelButton("Video settings", 'v', video, 0, 0);
	addPanelButton("Audio settings", 'a', audio, 0, 1);
	addPanelButton("Camera settings", 'c', camera, 0, 2);

	addPanelButton("General settings", 'g', general, 1, 0);
	addPanelButton("Display settings", 'd', display, 1, 1);
	addPanelButton("Radar settings", 'r', radar, 1, 2);

	if(gopts) {
	    main.add(new Button(200, "Switch character") {
		public void click() {
		    getparent(GameUI.class).act("lo", "cs");
		}
	    }, new Coord(0, 120));
	    main.add(new Button(200, "Log out") {
		public void click() {
		    getparent(GameUI.class).act("lo");
		}
	    }, new Coord(0, 150));
	}
	main.add(new Button(200, "Close") {
	    public void click() {
		OptWnd.this.hide();
	    }
	}, new Coord(0, 180));

	y = 0;
	audio.add(new Label("Master audio volume"), new Coord(0, y));
	y += 15;
	audio.add(new HSlider(200, 0, 1000, (int) (Audio.volume * 1000)) {
	    public void changed() {
		Audio.setvolume(val / 1000.0);
	    }
	}, new Coord(0, y));
	y += 30;
	audio.add(new Label("In-game event volume"), new Coord(0, y));
	y += 15;
	audio.add(new HSlider(200, 0, 1000, 0) {
	    protected void attach(UI ui) {
		super.attach(ui);
		val = (int) (ui.audio.pos.volume * 1000);
	    }

	    public void changed() {
		ui.audio.pos.setvolume(val / 1000.0);
	    }
	}, new Coord(0, y));
	y += 20;
	audio.add(new Label("Ambient volume"), new Coord(0, y));
	y += 15;
	audio.add(new HSlider(200, 0, 1000, 0) {
	    protected void attach(UI ui) {
		super.attach(ui);
		val = (int) (ui.audio.amb.volume * 1000);
	    }

	    public void changed() {
		ui.audio.amb.setvolume(val / 1000.0);
	    }
	}, new Coord(0, y));
	y += 35;
	audio.add(new PButton(200, "Back", 27, main), new Coord(0, y));
	audio.pack();

	initDisplayPanel();
	initGeneralPanel();
	initRadarPanel();
	initCameraPanel();
	main.pack();
	chpanel(main);
    }


    private void addPanelButton(String name, char key, Panel panel, int x, int y) {
	main.add(new PButton(200, name, key, panel), PANEL_POS.mul(x, y));
    }

    private void initCameraPanel() {
	int x = 0, y = 0, my = 0;

	int tx = x + camera.add(new Label("Camera:"), x, y).sz.x + 5;
	camera.add(new Dropbox<String>(100, 5, 16) {
	    @Override
	    protected String listitem(int i) {
		return new LinkedList<>(MapView.camlist()).get(i);
	    }

	    @Override
	    protected int listitems() {
		return MapView.camlist().size();
	    }

	    @Override
	    protected void drawitem(GOut g, String item, int i) {
		g.text(item, Coord.z);
	    }

	    @Override
	    public void change(String item) {
		super.change(item);
		MapView.defcam(item);
		if(ui.gui != null && ui.gui.map != null) {
		    ui.gui.map.camera = ui.gui.map.restorecam();
		}
	    }
	}, tx, y).sel = MapView.defcam();

	y += 35;
	camera.add(new Label("Brighten view"), x, y);
	y += 15;
	camera.add(new HSlider(200, 0, 500, 0) {
	    public void changed() {
		CFG.CAMERA_BRIGHT.set(val / 1000.0f);
		if(ui.sess != null && ui.sess.glob != null) {
		    ui.sess.glob.brighten();
		}
	    }
	}, x, y).val = (int) (1000 * CFG.CAMERA_BRIGHT.get());


	y += 25;
	my = Math.max(my, y);

	camera.add(new PButton(200, "Back", 27, main), 0, my + 35);
	camera.pack();
    }


    private void initGeneralPanel() {
	int x = 0;
	int y = 0, my = 0;
	general.add(new Dropbox<Locale>(100, 6, 16) {
	    @Override
	    protected Locale listitem(int i) {
		return L10N.locales.get(i);
	    }

	    @Override
	    protected int listitems() {
		return L10N.locales.size();
	    }

	    @Override
	    protected void drawitem(GOut g, Locale item, int i) {
		g.text(item.toString(), Q_TYPE_PADDING);
	    }

	    @Override
	    public void change(Locale item) {
		super.change(item);
		if(item != null) {
		    CFG.LANGUAGE.set(item);
		}
	    }
	}, x, y).sel = CFG.LANGUAGE.get();

	y += 25;
	general.add(new CFGBox("Store minimap tiles", CFG.STORE_MAP), x, y);

	y += 25;
	general.add(new CFGBox("Store chat logs", CFG.STORE_CHAT_LOGS, "Logs are stored in 'chats' folder"), new Coord(x, y));

	y += 25;
	general.add(new CFGBox("Single item CTRL choose", CFG.MENU_SINGLE_CTRL_CLICK, "If checked, will automatically select single item menus if CTRL is pressed when menu is opened."), x, y);

	my = Math.max(my, y);
	x += 250;
	y = 0;

	general.add(new Label("Choose menu items to select automatically:"), x, y);
	y += 15;
	final FlowerList list = general.add(new FlowerList(), x, y);

	y += list.sz.y + 5;
	final TextEntry value = general.add(new TextEntry(150, "") {
	    @Override
	    public void activate(String text) {
		list.add(text);
		settext("");
	    }
	}, x, y);

	general.add(new Button(45, "Add") {
	    @Override
	    public void click() {
		list.add(value.text);
		value.settext("");
	    }
	}, x + 155, y - 2);

	my = Math.max(my, y);

	general.add(new PButton(200, "Back", 27, main), 0, my + 35);
	general.pack();
    }

    private void initDisplayPanel() {
	int x = 0;
	int y = 0;
	int my = 0;
	display.add(new CFGBox("Always show kin names", CFG.DISPLAY_KINNAMES), new Coord(x, y));

	y += 25;
	display.add(new CFGBox("Show flavor objects", CFG.DISPLAY_FLAVOR), new Coord(x, y));

	y += 25;
	display.add(new CFGBox("Show gob info", CFG.DISPLAY_GOB_INFO, "Enables damage and crop/tree growth stage displaying", true), x, y);

	y += 25;
	display.add(new CFGBox("Show timestamps in chat messages", CFG.SHOW_CHAT_TIMESTAMP), new Coord(x, y));

	y += 25;
	display.add(new CFGBox("Undock minimap", CFG.MMAP_FLOAT, null, true) {
	    @Override
	    public void set(boolean a) {
		super.set(a);
		if(ui != null && ui.gui != null) {
		    ui.gui.showmmappanel(a);
		}
	    }
	}, x, y);

	y += 25;
	display.add(new CFGBox("Swap item quality and number", CFG.SWAP_NUM_AND_Q), x, y);

	y += 25;
	display.add(new CFGBox("Show item progress as number", CFG.PROGRESS_NUMBER), x, y);

	y += 25;
	display.add(new CFGBox("Show biomes on minimap", CFG.MMAP_SHOW_BIOMES), x, y);

	y += 25;
	display.add(new CFGBox("Simple crops", CFG.SIMPLE_CROPS, "Requires area reload"), x, y);

	y += 35;
	display.add(new CFGBox("Show object radius", CFG.SHOW_GOB_RADIUS, "Shows radius of mine supports, beehives etc.", true), x, y);

	y += 25;
	int w = display.add(new CFGBox("Show gob path", CFG.SHOW_GOB_PATH), x, y).sz.x;
	display.add(new IButton("gfx/hud/opt", "", "-d", "-h") {
	    @Override
	    public void click() {
		if(ui.gui != null) {
		    GobPathOptWnd.toggle(ui.gui);
		} else {
		    GobPathOptWnd.toggle(ui.root);
		}
	    }
	}, x + w + 5, y);

	my = Math.max(my, y);
	x += 250;
	y = 0;
	my = Math.max(my, y);
	int tx = x + display.add(new CFGBox("Show single quality as:", CFG.Q_SHOW_SINGLE), x, y).sz.x;

	Dropbox<QualityList.SingleType> dropbox = display.add(new Dropbox<QualityList.SingleType>(100, 6, 16) {
	    @Override
	    protected QualityList.SingleType listitem(int i) {
		return QualityList.SingleType.values()[i];
	    }

	    @Override
	    protected int listitems() {
		return QualityList.SingleType.values().length;
	    }

	    @Override
	    protected void drawitem(GOut g, QualityList.SingleType item, int i) {
		g.image(item.tex(), Q_TYPE_PADDING);
	    }

	    @Override
	    public void change(QualityList.SingleType item) {
		super.change(item);
		if(item != null) {
		    CFG.Q_SINGLE_TYPE.set(item);
		}
	    }
	}, tx + 5, y);
	dropbox.sel = CFG.Q_SINGLE_TYPE.get();

	y += 30;
	display.add(new CFGBox("Show all qualities on SHIFT", CFG.Q_SHOW_ALL_SHIFT), x, y);

	y += 25;
	display.add(new CFGBox("Show all qualities on CTRL", CFG.Q_SHOW_ALL_CTRL), x, y);

	y += 25;
	display.add(new CFGBox("Show all qualities on ALT", CFG.Q_SHOW_ALL_ALT), x, y);

	y += 35;
	display.add(new CFGBox("Show item durability", CFG.SHOW_ITEM_DURABILITY), new Coord(x, y));

	y += 25;
	display.add(new CFGBox("Show item wear bar", CFG.SHOW_ITEM_WEAR_BAR), new Coord(x, y));

	y += 25;
	display.add(new CFGBox("Show item armor", CFG.SHOW_ITEM_ARMOR), new Coord(x, y));

	y += 25;
	display.add(new CFGBox("Show hunger meter", CFG.HUNGER_METER) {
	    @Override
	    public void set(boolean a) {
		super.set(a);
		if(ui.gui != null && ui.gui.chrwdg != null) {
		    if(a) {
			ui.gui.addcmeter(new HungerMeter(ui.gui.chrwdg.glut));
		    } else {
			ui.gui.delcmeter(HungerMeter.class);
		    }
		}
	    }
	}, x, y);

	y += 25;
	display.add(new CFGBox("Show FEP meter", CFG.FEP_METER) {
	    @Override
	    public void set(boolean a) {
		super.set(a);
		if(ui.gui != null && ui.gui.chrwdg != null) {
		    if(a) {
			ui.gui.addcmeter(new FEPMeter(ui.gui.chrwdg.feps));
		    } else {
			ui.gui.delcmeter(FEPMeter.class);
		    }
		}
	    }
	}, x, y);

	my = Math.max(my, y);

	display.add(new PButton(200, "Back", 27, main), new Coord(0, my + 35));
	display.pack();
    }

    private void initRadarPanel() {
	final WidgetList<RadarCFG.MarkerCheck> markers = new WidgetList<RadarCFG.MarkerCheck>(new Coord(200, 16), 20) {
	    @Override
	    protected void itemclick(RadarCFG.MarkerCheck item, int button) {
		if(button == 1) {
		    item.set(!item.a);
		}
	    }
	};
	markers.canselect = false;
	radar.add(markers, 225, 0);

	WidgetList<RadarCFG.GroupCheck> groups = radar.add(new WidgetList<RadarCFG.GroupCheck>(new Coord(200, 16), 20) {
	    @Override
	    public void selected(RadarCFG.GroupCheck item) {
		markers.clear(true);
		for(RadarCFG.MarkerCFG marker : item.group.markerCFGs) {
		    markers.additem(new RadarCFG.MarkerCheck(marker));
		}
	    }
	});
	for(RadarCFG.Group group : RadarCFG.groups) {
	    groups.additem(new RadarCFG.GroupCheck(group)).hitbox = true;
	}

	radar.add(new Button(60, "Save"){
	    @Override
	    public void click() {
		RadarCFG.save();
	    }
	}, 183, groups.sz.y + 10);

	radar.pack();
	radar.add(new PButton(200, "Back", 27, main), radar.sz.x / 2 - 100, radar.sz.y + 35);
	radar.pack();
    }

    public OptWnd() {
	this(true);
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if((sender == this) && (msg == "close")) {
	    hide();
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }

    public void show() {
	chpanel(main);
	super.show();
    }

    @Override
    public void destroy() {
	GobPathOptWnd.remove();
	super.destroy();
    }

    public static class CFGBox extends CheckBox implements CFG.Observer<Boolean> {

	protected final CFG<Boolean> cfg;

	public CFGBox(String lbl, CFG<Boolean> cfg) {
	    this(lbl, cfg, null, false);
	}

	public CFGBox(String lbl, CFG<Boolean> cfg, String tip) {
	    this(lbl, cfg, tip, false);
	}

	public CFGBox(String lbl, CFG<Boolean> cfg, String tip, boolean observe) {
	    super(lbl);

	    this.cfg = cfg;
	    defval();
	    if(tip != null) {
		tooltip = Text.render(tip).tex();
	    }
	    if(observe){ cfg.observe(this); }
	}

	protected void defval() {
	    a = cfg.get();
	}

	@Override
	public void set(boolean a) {
	    this.a = a;
	    cfg.set(a);
	}

	@Override
	public void destroy() {
	    cfg.unobserve(this);
	    super.destroy();
	}

	@Override
	public void updated(CFG<Boolean> cfg) {
	    a = cfg.get();
	}
    }
}
