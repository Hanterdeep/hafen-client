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

import haven.MCache.Grid;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import haven.resutil.Ridges;

import static haven.MCache.*;

public class LocalMiniMap extends Widget {
    private static final Tex mapgrid = Resource.loadtex("gfx/hud/mmap/mapgrid");
    public static final Coord VIEW_SZ = MCache.sgridsz.mul(9).div(tilesz2);// view radius is 9x9 "server" grids
    public static final Color VIEW_BG_COLOR = new Color(255, 255, 255, 60);
    public static final Color VIEW_BORDER_COLOR = new Color(0, 0, 0, 128);
    public final MapView mv;
    private String biome;
    private Tex biometex = null;
    private long session = 0;

    private Coord cc = null;
    private final Map<Coord, Defer.Future<MapTile>> cache = new LinkedHashMap<Coord, Defer.Future<MapTile>>(5, 0.75f, true) {
	protected boolean removeEldestEntry(Map.Entry<Coord, Defer.Future<MapTile>> eldest) {
	    if(size() > 100) {
		    clearCacheTile(eldest.getValue());
		    return(true);
	    }
	    return(false);
	}
    };
    private Coord off = new Coord (0,0);
    private Coord doff = null;
    
    public static class MapTile {
	public final Tex img;
	public final Coord ul, c;
	public final Grid grid;
	public final int seq;
	
	public MapTile(Tex img, Coord ul, Coord c, Grid grid, int seq) {
	    this.img = img;
	    this.ul = ul;
	    this.c = c;
	    this.grid = grid;
	    this.seq = seq;
	}
    }

    private BufferedImage tileimg(int t, BufferedImage[] texes) {
	BufferedImage img = texes[t];
	if(img == null) {
	    Resource r = ui.sess.glob.map.tilesetr(t);
	    if(r == null)
		return(null);
	    Resource.Image ir = r.layer(Resource.imgc);
	    if(ir == null)
		return(null);
	    img = ir.img;
	    texes[t] = img;
	}
	return(img);
    }
    
    public BufferedImage drawmap(Coord ul, Coord sz) {
	BufferedImage[] texes = new BufferedImage[256];
	MCache m = ui.sess.glob.map;
	BufferedImage buf = TexI.mkbuf(sz);
	Coord c = new Coord();
	for(c.y = 0; c.y < sz.y; c.y++) {
	    for(c.x = 0; c.x < sz.x; c.x++) {
		int t = m.gettile(ul.add(c));
		BufferedImage tex = tileimg(t, texes);
		int rgb = 0xffffffff;
		if(tex != null)
		    rgb = tex.getRGB(Utils.floormod(c.x + ul.x, tex.getWidth()),
				     Utils.floormod(c.y + ul.y, tex.getHeight()));
		buf.setRGB(c.x, c.y, rgb);
	    }
	}
	for(c.y = 1; c.y < sz.y - 1; c.y++) {
	    for(c.x = 1; c.x < sz.x - 1; c.x++) {
		int t = m.gettile(ul.add(c));
		Tiler tl = m.tiler(t);
		if(tl instanceof Ridges.RidgeTile) {
		    if(Ridges.brokenp(m, ul.add(c))) {
			for(int y = c.y - 1; y <= c.y + 1; y++) {
			    for(int x = c.x - 1; x <= c.x + 1; x++) {
				Color cc = new Color(buf.getRGB(x, y));
				buf.setRGB(x, y, Utils.blendcol(cc, Color.BLACK, ((x == c.x) && (y == c.y))?1:0.1).getRGB());
			    }
			}
		    }
		}
	    }
	}
	for(c.y = 1; c.y < sz.y-1; c.y++) {
	    for(c.x = 1; c.x < sz.x-1; c.x++) {
		int t = m.gettile(ul.add(c));
		if((m.gettile(ul.add(c).add(-1, 0)) > t) ||
		   (m.gettile(ul.add(c).add( 1, 0)) > t) ||
		   (m.gettile(ul.add(c).add(0, -1)) > t) ||
		   (m.gettile(ul.add(c).add(0,  1)) > t))
		    buf.setRGB(c.x, c.y, Color.BLACK.getRGB());
	    }
	}
	return(buf);
    }

    public LocalMiniMap(Coord sz, MapView mv) {
	super(sz);
	this.mv = mv;
    }
    
    public Coord p2c(Coord pc) {
	return(pc.div(tilesz2).sub(cc.add(off)).add(sz.div(2)));
    }

    public Coord c2p(Coord c) {
	return(c.sub(sz.div(2)).add(cc.add(off)).mul(tilesz2).add(tilesz2.div(2)));
    }

    public void drawicons(GOut g) {

	synchronized (Radar.markers) {
	    for (Radar.Marker marker : Radar.markers) {
		if(marker.gob.id == mv.plgob){
		    continue;
		}
		try {
		    Coord gc = p2c(marker.gob.rc.round());
		    Tex tex = marker.tex();
		    if(tex != null) {
			g.chcolor(marker.color());
			g.aimage(tex, gc, 0.5, 0.5);
		    }
		} catch (Loading ignored) {
		}
	    }
	}
	g.chcolor();
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
	Gob gob = findicongob(c);
	if(gob != null) {
	    Radar.Marker icon = gob.getattr(Radar.Marker.class);
	    if(icon != null) {
		return icon.tooltip(ui.modshift);
	    }
	}
	return super.tooltip(c, prev);
    }

    public Gob findicongob(Coord c) {
	synchronized (Radar.markers) {
	    ListIterator<Radar.Marker> li = Radar.markers.listIterator(Radar.markers.size());
	    while(li.hasPrevious()) {
		Radar.Marker icon = li.previous();
		try {
		    Gob gob = icon.gob;
		    if(gob.id == mv.plgob || gob.rc == null){
			continue;
		    }
		    Tex tex = icon.tex();
		    if(tex != null) {
			Coord gc = p2c(gob.rc.round());
			Coord sz = tex.sz();
			if(c.isect(gc.sub(sz.div(2)), sz))
			    return (gob);
		    }
		} catch (Loading ignored) {}
	    }
	}
	return(null);
    }

    public void tick(double dt) {
	Gob pl = ui.sess.glob.oc.getgob(mv.plgob);
	if(pl == null)
	    this.cc = mv.cc.round().div(tilesz2);
	else
	    this.cc = pl.rc.round().div(tilesz2);

	Coord mc = rootxlate(ui.mc);
	if(mc.isect(Coord.z, sz)) {
	    setBiome(c2p(mc).div(tilesz2));
	} else {
	    setBiome(cc);
	}
    }

    private void setBiome(Coord c) {
	try {
	    if (c.div(cmaps).manhattan2(cc.div(cmaps)) > 1) {return;}
	    int t = mv.ui.sess.glob.map.gettile(c);
	    Resource r = ui.sess.glob.map.tilesetr(t);
	    String newbiome;
	    if(r != null) {
		newbiome = (r.name);
	    } else {
		newbiome = "Void";
	    }
	    if(!newbiome.equals(biome)){
		biome = newbiome;
		biometex = Text.renderstroked(prettybiome(biome)).tex();
	    }
	}catch (Loading ignored){}
    }

    public void draw(GOut g) {
	if(cc == null)
	    return;
	Coord plg = cc.div(cmaps);
	try {
	    if(MapDumper.session() != session) {
		session = MapDumper.session();
		clearCache();
	    }
	} catch (Loading ignored) {}

	Coord center = cc.add(off);
	Coord hsz = sz.div(2);

	Coord ulg = center.sub(hsz).div(cmaps);
	Coord brg = center.add(hsz).div(cmaps);

	Coord cur = new Coord();
	for(cur.x = ulg.x; cur.x <= brg.x; cur.x++) {
	    for(cur.y = ulg.y; cur.y <= brg.y; cur.y++) {
		Defer.Future<MapTile> f;
		synchronized (cache) {
		    f = cache.get(cur);
		    if (cur.manhattan2(plg) <= 1) {
			final Grid grid;
			try {
			    grid = ui.sess.glob.map.getgrid(new Coord(cur));
			} catch (Loading e) { continue; }
			final int seq = grid.seq;
			if (f == null || (f.done() && (f.get().grid.id != grid.id || f.get().seq != seq))) {
			    final Coord tmp = new Coord(cur);
			    f = Defer.later(new Defer.Callable<MapTile>() {
				public MapTile call() {
				    Coord ul = tmp.mul(cmaps);
				    BufferedImage drawmap = drawmap(ul, cmaps);
				    return (new MapTile(new TexI(drawmap), ul, tmp, grid, seq));
				}
			    });
			    cache.put(tmp, f);
			}
		    }
		}
		if(f != null && f.done()){
		    MapTile map = f.get();
		    Coord tc = map.ul.sub(center).add(hsz);
		    //g.image(MiniMap.bg, tc);
		    g.image(map.img, tc);
		    if(CFG.MMAP_GRID.get()) {
			g.image(mapgrid, tc);
		    }

		}
	    }
	}
	if(CFG.MMAP_VIEW.get()) {
	    Gob pl = ui.sess.glob.oc.getgob(mv.plgob);
	    if(pl != null) {
		Coord rc = p2c(pl.rc.round().div(MCache.sgridsz).sub(4, 4).mul(MCache.sgridsz));
		g.chcolor(VIEW_BG_COLOR);
		g.frect(rc, VIEW_SZ);
		g.chcolor(VIEW_BORDER_COLOR);
		g.rect(rc, VIEW_SZ);
		g.chcolor();
	    }
	}
	drawicons(g);
	try {
	    synchronized (ui.sess.glob.party.memb) {
		for (Party.Member m : ui.sess.glob.party.memb.values()) {
		    Coord ptc;
		    try {
			ptc = m.getc().round();
		    } catch (MCache.LoadingMap e) {
			ptc = null;
		    }
		    if(ptc == null)
			continue;
		    ptc = p2c(ptc);
		    g.chcolor(m.col);
		    g.aimage(MiniMap.plx.layer(Resource.imgc).tex(), ptc, 0.5, 0.5);
		    g.chcolor();
		}
	    }
	} catch(Loading ignored) {}
	if(CFG.MMAP_SHOW_BIOMES.get()) {
	    Coord mc = rootxlate(ui.mc);
	    if(mc.isect(Coord.z, sz)) {
		setBiome(c2p(mc).div(tilesz2));
	    } else {
		setBiome(cc);
	    }
	    if(biometex != null) {g.image(biometex, Coord.z);}
	}
    }

    private void clearCache() {
	synchronized (cache){
	    Collection<Defer.Future<MapTile>> tiles = cache.values();
	    for(Defer.Future<MapTile> tile : tiles){
		clearCacheTile(tile);
	    }
	    cache.clear();
	}
    }

    private void clearCacheTile(Defer.Future<MapTile> tile) {
	try {
	    if(tile.done()){
		MapTile t = tile.get();
		t.img.dispose();
	    } else {
		tile.cancel();
	    }
	} catch (RuntimeException ignored) {}
    }

	public boolean mousedown(Coord c, int button) {
	if(cc == null)
	    return(false);
	Gob gob = findicongob(c);
	if(gob == null)
	    mv.wdgmsg("click", rootpos().add(c), c2p(c), button, ui.modflags());
	else
	    mv.wdgmsg("click", rootpos().add(c), c2p(c), button, ui.modflags(), 0, (int)gob.id, gob.rc, 0, -1);
	if(button == 3){
	    doff = c;
	} else if(button == 2){
	    off = new Coord();
	}
	return(true);
    }


    @Override
    public void mousemove(Coord c) {
	if(doff != null){
	    off = off.add(doff.sub(c));
	    doff = c;
	}
	super.mousemove(c);
    }

    @Override
    public boolean mouseup(Coord c, int button) {
	if(button == 3){
	    doff = null;
	}
	return super.mouseup(c, button);
    }

    private static String prettybiome(String biome){
	int k = biome.lastIndexOf("/");
	biome = biome.substring(k+1);
	biome = biome.substring(0,1).toUpperCase() + biome.substring(1);
	return biome;
    }
}
