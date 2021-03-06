package mindustry.ui.dialogs;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.ArcAnnotate.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.storage.CoreBlock.*;

import static mindustry.Vars.*;
import static mindustry.graphics.g3d.PlanetRenderer.*;
import static mindustry.ui.dialogs.PlanetDialog.Mode.*;

public class PlanetDialog extends BaseDialog implements PlanetInterfaceRenderer{
    final FrameBuffer buffer = new FrameBuffer(2, 2, true);
    final PlanetRenderer planets = renderer.planets;
    final LaunchLoadoutDialog loadouts = new LaunchLoadoutDialog();
    final Table stable  = new Table().background(Styles.black3);

    int launchRange;
    float zoom = 1f, selectAlpha = 1f;
    @Nullable Sector selected, hovered, launchSector;
    CoreBuild launcher;
    Mode mode = look;
    boolean launching;
    Cons<Sector> listener = s -> {};

    public PlanetDialog(){
        super("", Styles.fullDialog);

        shouldPause = true;

        getCell(buttons).padBottom(-4);
        buttons.background(Styles.black).defaults().growX().height(64f).pad(0);

        keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back){
                Core.app.post(this::hide);
            }
        });

        dragged((cx, cy) -> {
            Vec3 pos = planets.camPos;

            float upV = pos.angle(Vec3.Y);
            float xscale = 9f, yscale = 10f;
            float margin = 1;

            //scale X speed depending on polar coordinate
            float speed = 1f - Math.abs(upV - 90) / 90f;

            pos.rotate(planets.cam.up, cx / xscale * speed);

            //prevent user from scrolling all the way up and glitching it out
            float amount = cy / yscale;
            amount = Mathf.clamp(upV + amount, margin, 180f - margin) - upV;

            pos.rotate(Tmp.v31.set(planets.cam.up).rotate(planets.cam.direction, 90), amount);
        });

        scrolled(value -> {
            zoom = Mathf.clamp(zoom + value / 10f, 0.5f, 2f);
        });

        shown(this::setup);
    }

    /** show with no limitations, just as a map. */
    @Override
    public Dialog show(){
        mode = look;
        selected = hovered = launchSector = null;
        launching = false;
        if(planets.planet.getLastSector() != null){
            lookAt(planets.planet.getLastSector());
        }
        return super.show();
    }

    public void showSelect(Sector sector, Cons<Sector> listener){
        selected = null;
        hovered = null;
        launching = false;
        this.listener = listener;

        //update view to sector
        lookAt(sector);
        zoom = 1f;
        planets.zoom = 2f;
        selectAlpha = 0f;
        launchSector = sector;

        mode = select;

        super.show();
    }

    public void showLaunch(Sector sector, CoreBuild launcher){
        if(launcher == null) return;

        this.launcher = launcher;
        selected = null;
        hovered = null;
        launching = false;

        //update view to sector
        lookAt(sector);
        zoom = 1f;
        planets.zoom = 2f;
        selectAlpha = 0f;
        launchRange = ((CoreBlock)launcher.block).launchRange;
        launchSector = sector;

        mode = launch;

        super.show();
    }

    private void lookAt(Sector sector){
        planets.camPos.set(Tmp.v33.set(sector.tile.v).rotate(Vec3.Y, -sector.planet.getRotation()));
    }

    boolean canSelect(Sector sector){
        if(mode == select) return sector.hasBase();

        return mode == launch &&
            (sector.tile.v.within(launchSector.tile.v, (launchRange + 0.5f) * planets.planet.sectorApproxRadius*2) //within range
            || (sector.preset != null && sector.preset.unlocked())); //is an unlocked preset
    }

    @Override
    public void renderSectors(Planet planet){

        //draw all sector stuff
        for(Sector sec : planet.sectors){

            if(selectAlpha > 0.01f){
                if(canSelect(sec) || sec.unlocked()){
                    if(sec.baseCoverage > 0){
                        planets.fill(sec, Tmp.c1.set(Team.crux.color).a(0.5f * sec.baseCoverage * selectAlpha), -0.002f);
                    }

                    Color color =
                    sec.hasBase() ? Team.sharded.color :
                    sec.preset != null ? Team.derelict.color :
                    sec.hasEnemyBase() ? Team.crux.color :
                    null;

                    if(color != null){
                        planets.drawSelection(sec, Tmp.c1.set(color).mul(0.8f).a(selectAlpha), 0.026f, -0.001f);
                    }
                }else{
                    planets.fill(sec, Tmp.c1.set(shadowColor).mul(1, 1, 1, selectAlpha), -0.001f);
                }
            }
        }

        if(launchSector != null){
            planets.fill(launchSector, hoverColor, -0.001f);
        }

        //draw hover border
        if(hovered != null){
            planets.fill(hovered, hoverColor, -0.001f);
            planets.drawBorders(hovered, borderColor);
        }

        //draw selected borders
        if(selected != null){
            planets.drawSelection(selected);
            planets.drawBorders(selected, borderColor);
        }

        planets.batch.flush(Gl.triangles);

        if(mode == launch || mode == select){
            if(hovered != launchSector && hovered != null && canSelect(hovered)){
                planets.drawArc(planet, launchSector.tile.v, hovered.tile.v);
            }
        }

        /*
        //TODO render arcs
        if(selected != null && selected.preset != null){
            for(Objective o : selected.preset.requirements){
                if(o instanceof SectorObjective){
                    SectorPreset preset = ((SectorObjective)o).preset;
                    planets.drawArc(planet, selected.tile.v, preset.sector.tile.v);
                }
            }
        }*/
    }

    @Override
    public void renderProjections(){
        if(hovered != null){
            planets.drawPlane(hovered, () -> {
                Draw.color(Color.white, Pal.accent, Mathf.absin(5f, 1f));

                TextureRegion icon = hovered.locked() && !canSelect(hovered) ? Icon.lock.getRegion() : null;

                if(icon != null){
                    Draw.rect(icon, 0, 0);
                }

                Draw.reset();
            });
        }
    }

    void setup(){
        zoom = planets.zoom = 1f;
        selectAlpha = 1f;

        cont.clear();
        titleTable.remove();


        cont.stack(
        new Element(){
            {
                //add listener to the background rect, so it doesn't get unnecessary touch input
                addListener(new ElementGestureListener(){
                    @Override
                    public void tap(InputEvent event, float x, float y, int count, KeyCode button){
                        if(hovered != null && (mode == launch ? canSelect(hovered) && hovered != launchSector : hovered.unlocked())){
                            selected = hovered;
                        }

                        if(selected != null){
                            updateSelected();
                        }
                    }
                });
            }

            @Override
            public void draw(){
                planets.render(PlanetDialog.this);
                Core.scene.setScrollFocus(PlanetDialog.this);
            }
        },
        new Table(t -> {
            //TODO localize
            t.top();
            t.label(() -> mode == select ? "@sectors.select" : mode == launch ? "Select Launch Sector" : "Turn " + universe.turn()).style(Styles.outlineLabel).color(Pal.accent);
        })).grow();

    }

    @Override
    public void draw(){
        boolean doBuffer = color.a < 0.99f;

        if(doBuffer){
            buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
            buffer.begin(Color.clear);
        }

        super.draw();

        if(doBuffer){
            buffer.end();

            Draw.color(color);
            Draw.rect(Draw.wrap(buffer.getTexture()), width/2f, height/2f, width, -height);
            Draw.color();
        }
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(selected != null){
            addChild(stable);
            Vec3 pos = planets.cam.project(Tmp.v31.set(selected.tile.v).setLength(PlanetRenderer.outlineRad).rotate(Vec3.Y, -planets.planet.getRotation()).add(planets.planet.position));
            stable.setPosition(pos.x, pos.y, Align.center);
            stable.toFront();

            //smooth camera toward the sector
            if(mode == launch && launching){
                float len = planets.camPos.len();
                planets.camPos.slerp(Tmp.v31.set(selected.tile.v).rotate(Vec3.Y,-selected.planet.getRotation()).setLength(len), 0.1f);
            }
        }else{
            stable.remove();
        }

        if(planets.planet.isLandable()){
            hovered = planets.planet.getSector(planets.cam.getMouseRay(), PlanetRenderer.outlineRad);
        }else{
            hovered = selected = null;
        }

        planets.zoom = Mathf.lerpDelta(planets.zoom, zoom, 0.4f);
        selectAlpha = Mathf.lerpDelta(selectAlpha, Mathf.num(planets.zoom < 1.9f), 0.1f);
    }

    void updateSelected(){
        Sector sector = selected;

        if(sector == null){
            stable.remove();
            return;
        }

        float x = stable.getX(Align.center), y = stable.getY(Align.center);
        stable.clear();
        stable.background(Styles.black6);

        stable.add("[accent]" + (sector.preset == null ? sector.id : sector.preset.localizedName)).row();
        stable.image().color(Pal.accent).fillX().height(3f).pad(3f).row();
        stable.add(sector.save != null ? sector.save.getPlayTime() : "@sectors.unexplored").row();
        if(sector.hasWaves() || sector.hasEnemyBase()){
            stable.add("[accent]Difficulty: " + (int)(sector.baseCoverage * 10)).row();
        }

        if(sector.hasBase() && sector.hasWaves()){
            //TODO localize when finalized
            //these mechanics are likely to change and as such are not added to the bundle
            stable.add("[scarlet]Under attack!");
            stable.row();
            stable.add("[accent]" + Mathf.ceil(sectorDestructionTurns - (sector.getSecondsPassed() * 60) / turnDuration) + " turn(s)\nuntil destruction");
            stable.row();
        }

        if(sector.save != null){
            stable.add("@sectors.resources").row();
            stable.table(t -> {

                if(sector.save != null && sector.save.meta.secinfo != null && sector.save.meta.secinfo.resources.any()){
                    t.left();
                    int idx = 0;
                    int max = 5;
                    for(UnlockableContent c : sector.save.meta.secinfo.resources){
                        t.image(c.icon(Cicon.small)).padRight(3);
                        if(++idx % max == 0) t.row();
                    }
                }else{
                    t.add("@unknown").color(Color.lightGray);
                }


            }).fillX().row();
        }

        //production
        if(sector.hasBase() && sector.save.meta.hasProduction){
            stable.add("@sectors.production").row();
            stable.table(t -> {
                t.left();

                sector.save.meta.secinfo.production.each((item, stat) -> {
                    int total = (int)(stat.mean * 60);
                    if(total > 1){
                        t.image(item.icon(Cicon.small)).padRight(3);
                        t.add(UI.formatAmount(total) + " " + Core.bundle.get("unit.perminute")).color(Color.lightGray);
                        t.row();
                    }
                });
            }).row();
        }

        //stored resources
        if(sector.hasBase() && sector.save.meta.secinfo.coreItems.size > 0){
            stable.add("@sectors.stored").row();
            stable.table(t -> {
                t.left();

                t.table(res -> {
                    ItemSeq items = sector.calculateItems();

                    int i = 0;
                    for(ItemStack stack : items){
                        res.image(stack.item.icon(Cicon.small)).padRight(3);
                        res.add(UI.formatAmount(stack.amount)).color(Color.lightGray);
                        if(++i % 2 == 0){
                            res.row();
                        }
                    }
                });

            }).row();
        }

        stable.row();

        if((sector.hasBase() && mode == look) || canSelect(sector) || (sector.preset != null && sector.preset.alwaysUnlocked)){
            stable.button(mode == select ? "@sectors.select" : sector.hasBase() ? "@sectors.resume" : "@sectors.launch", Styles.transt, () -> {

                boolean shouldHide = true;

                //save before launch.
                if(control.saves.getCurrent() != null && state.isGame() && mode != select){
                    try{
                        control.saves.getCurrent().save();
                    }catch(Throwable e){
                        e.printStackTrace();
                        ui.showException("[accent]" + Core.bundle.get("savefail"), e);
                    }
                }

                if(mode == launch && !sector.hasBase()){
                    Sector current = state.rules.sector;
                    shouldHide = false;
                    loadouts.show((CoreBlock)launcher.block, launcher, () -> {
                        control.handleLaunch(launcher);
                        launching = true;
                        zoom = 0.5f;

                        ui.hudfrag.showLaunchDirect();
                        Time.runTask(launchDuration, () -> control.playSector(current, sector));
                    });
                }else if(mode == select){
                    listener.get(sector);
                }else{
                    control.playSector(sector);
                }

                if(shouldHide) hide();
            }).growX().padTop(2f).height(50f).minWidth(170f).disabled(b -> state.rules.sector == sector && !state.isMenu());
        }

        stable.pack();
        stable.setPosition(x, y, Align.center);

        stable.update(() -> {
            if(selected != null){
                if(launching){
                    stable.color.sub(0, 0, 0, 0.05f * Time.delta);
                }else{
                    //fade out UI when not facing selected sector
                    Tmp.v31.set(selected.tile.v).rotate(Vec3.Y, -planets.planet.getRotation()).scl(-1f).nor();
                    float dot = planets.cam.direction.dot(Tmp.v31);
                    stable.color.a = Math.max(dot, 0f)*2f;
                    if(dot*2f <= -0.1f){
                        stable.remove();
                        selected = null;
                    }
                }

            }
        });

        stable.act(0f);
    }

    enum Mode{
        /** Look around for existing sectors. Can only deploy. */
        look,
        /** Launch to a new location. */
        launch,
        /** Select a sector for some purpose. */
        select
    }
}
