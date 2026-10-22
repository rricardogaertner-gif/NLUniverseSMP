package nl.universe.smp;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.*;

public final class SMPPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID,Long> bans = new HashMap<>();
    private final Map<UUID,Integer> kills = new HashMap<>(), deaths = new HashMap<>();
    private final Map<UUID,Long> joined = new HashMap<>(), survival = new HashMap<>();
    private final Map<UUID,Location> last = new HashMap<>();
    private final Set<UUID> smpInventory = new HashSet<>();
    private final Set<Location> drops = new HashSet<>();
    private File stateFile, playersDir;
    private YamlConfiguration state;
    private long seasonEnd;
    private boolean wiping;
    private final Set<String> allowed = new HashSet<>();
    private final Map<UUID,Boolean> changing = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        String hub = getConfig().getString("hub.world","hub");
        for (String path: List.of("worlds.overworld","worlds.nether","worlds.end")) {
            String name=getConfig().getString(path, "");
            if(name.isBlank() || name.equalsIgnoreCase(hub) || name.equalsIgnoreCase("world") || name.equalsIgnoreCase("world_nether") || name.equalsIgnoreCase("world_the_end") || !name.matches("[A-Za-z0-9_-]+") || !allowed.add(name)) {
                getLogger().severe("UNSAFE world configuration: "+path+" = "+name+". Plugin disabled.");
                getServer().getPluginManager().disablePlugin(this); return;
            }
        }
        playersDir=new File(getDataFolder(),"players"); playersDir.mkdirs();
        stateFile=new File(getDataFolder(),"state.yml"); state=YamlConfiguration.loadConfiguration(stateFile);
        seasonEnd=state.getLong("season-end",0L);
        if(seasonEnd<=0) {seasonEnd=System.currentTimeMillis()+days()*86400000L; saveState();}
        for(String name:allowed) if(Bukkit.getWorld(name)==null) {
            World.Environment env=name.equals(nether())?World.Environment.NETHER:name.equals(end())?World.Environment.THE_END:World.Environment.NORMAL;
            Bukkit.createWorld(new WorldCreator(name).environment(env).generateStructures(true));
        }
        configureWorlds();
        loadPlayers();
        Objects.requireNonNull(getCommand("nlsmp")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this,this);
        for(Player p:Bukkit.getOnlinePlayers()) if(isSmp(p.getWorld())) smpInventory.add(p.getUniqueId());
        Bukkit.getScheduler().runTaskTimer(this,this::tick,20L,20L*30);
        int minutes=Math.max(1,getConfig().getInt("supply-drops.interval-minutes",60));
        Bukkit.getScheduler().runTaskTimer(this,this::spawnDrop,20L*60*minutes,20L*60*minutes);
        getLogger().info("NLUniverseSMP active. Season ends "+Instant.ofEpochMilli(seasonEnd));
    }
    @Override public void onDisable() {for(Player p:Bukkit.getOnlinePlayers()) if(smpInventory.contains(p.getUniqueId())) saveInventory(p,true);saveState();}
    private String overworld(){return getConfig().getString("worlds.overworld","smp");}
    private String nether(){return getConfig().getString("worlds.nether","smp_nether");}
    private String end(){return getConfig().getString("worlds.end","smp_the_end");}
    private int days(){return Math.max(1,getConfig().getInt("season-days",14));}
    private boolean isSmp(World w){return w!=null && allowed.contains(w.getName());}
    private void configureWorlds(){for(String name:allowed){World w=Bukkit.getWorld(name);if(w==null)continue;w.setPVP(getConfig().getBoolean("pvp",true));WorldBorder border=w.getWorldBorder();border.setCenter(0,0);border.setSize(getConfig().getDouble("world-border-diameter",10000));}}
    private Location hub(){World w=Bukkit.getWorld(getConfig().getString("hub.world","hub"));if(w==null)return null;return new Location(w,getConfig().getDouble("hub.x",0.5),getConfig().getDouble("hub.y",80),getConfig().getDouble("hub.z",0.5),(float)getConfig().getDouble("hub.yaw",0),(float)getConfig().getDouble("hub.pitch",0));}
    private boolean safeSmpLocation(Location loc) {
        if (loc == null || loc.getWorld() == null || !isSmp(loc.getWorld())) return false;
        World w = loc.getWorld();
        if (!Double.isFinite(loc.getX()) || !Double.isFinite(loc.getY()) || !Double.isFinite(loc.getZ())) return false;
        if (loc.getY() < w.getMinHeight() + 1 || loc.getY() >= w.getMaxHeight() - 2) return false;
        if (!w.getWorldBorder().isInside(loc)) return false;
        Block feet = loc.getBlock();
        return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
            && feet.getRelative(0, -1, 0).getType().isSolid()
            && !feet.isLiquid() && !feet.getRelative(0, 1, 0).isLiquid();
    }
    private Location start() {
        World w = Bukkit.getWorld(overworld());
        if (w == null) return null;
        Location spawn = w.getSpawnLocation().clone().add(0.5, 0, 0.5);
        if (safeSmpLocation(spawn)) return spawn;
        // Search near the world-border center, never at an old out-of-border spawn.
        for (int radius = 0; radius <= 128; radius += 8) {
            for (int dx = -radius; dx <= radius; dx += 8) {
                for (int dz = -radius; dz <= radius; dz += 8) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = dx, z = dz;
                    if (!w.getWorldBorder().isInside(new Location(w, x + 0.5, 80, z + 0.5))) continue;
                    int y = w.getHighestBlockYAt(x, z) + 1;
                    Location candidate = new Location(w, x + 0.5, y, z + 0.5);
                    if (safeSmpLocation(candidate)) {
                        w.setSpawnLocation(candidate);
                        return candidate;
                    }
                }
            }
        }
        getLogger().severe("No safe SMP spawn found near 0,0; teleport blocked.");
        return null;
    }
    private void loadPlayers(){for(String id:state.getConfigurationSection("players")==null?Set.<String>of():state.getConfigurationSection("players").getKeys(false)){try{UUID u=UUID.fromString(id);String p="players."+id+".";long b=state.getLong(p+"ban",0);if(b>0)bans.put(u,b);kills.put(u,state.getInt(p+"kills",0));deaths.put(u,state.getInt(p+"deaths",0));survival.put(u,state.getLong(p+"survival",0));String worldName=state.getString(p+"last.world");World lw=worldName==null?null:Bukkit.getWorld(worldName);if(lw!=null&&isSmp(lw))last.put(u,new Location(lw,state.getDouble(p+"last.x"),state.getDouble(p+"last.y"),state.getDouble(p+"last.z"),(float)state.getDouble(p+"last.yaw"),(float)state.getDouble(p+"last.pitch")));}catch(IllegalArgumentException ignored){}}}
    private void saveState(){if(state==null)return;state.set("season-end",seasonEnd);for(UUID u:union()){String p="players."+u+".";state.set(p+"ban",bans.getOrDefault(u,0L));state.set(p+"kills",kills.getOrDefault(u,0));state.set(p+"deaths",deaths.getOrDefault(u,0));state.set(p+"survival",survival.getOrDefault(u,0L));Location loc=last.get(u);if(loc!=null){state.set(p+"last.world",loc.getWorld().getName());state.set(p+"last.x",loc.getX());state.set(p+"last.y",loc.getY());state.set(p+"last.z",loc.getZ());state.set(p+"last.yaw",loc.getYaw());state.set(p+"last.pitch",loc.getPitch());}}try{state.save(stateFile);}catch(IOException e){getLogger().severe("Failed saving state: "+e.getMessage());}}
    private Set<UUID> union(){Set<UUID> s=new HashSet<>(bans.keySet());s.addAll(kills.keySet());s.addAll(deaths.keySet());s.addAll(survival.keySet());s.addAll(last.keySet());return s;}
    private File invFile(UUID id,boolean smp){return new File(playersDir,id+(smp?"-smp":"-outside")+".yml");}
    private void saveInventory(Player p,boolean smp){YamlConfiguration y=new YamlConfiguration();y.set("inventory",Arrays.asList(p.getInventory().getStorageContents()));y.set("armor",Arrays.asList(p.getInventory().getArmorContents()));y.set("offhand",p.getInventory().getItemInOffHand());y.set("ender",Arrays.asList(p.getEnderChest().getContents()));y.set("xp-level",p.getLevel());y.set("xp-progress",p.getExp());y.set("food",p.getFoodLevel());y.set("saturation",p.getSaturation());try{y.save(invFile(p.getUniqueId(),smp));}catch(IOException e){getLogger().severe("Inventory save failed: "+e.getMessage());}}
    private void loadInventory(Player p,boolean smp){File f=invFile(p.getUniqueId(),smp);p.getInventory().clear();p.getEnderChest().clear();p.setLevel(0);p.setExp(0);p.setFoodLevel(20);p.setSaturation(5);if(!f.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(f);p.getInventory().setStorageContents(items(y.getList("inventory"),36));p.getInventory().setArmorContents(items(y.getList("armor"),4));ItemStack off=y.getItemStack("offhand");if(off!=null)p.getInventory().setItemInOffHand(off);p.getEnderChest().setContents(items(y.getList("ender"),27));p.setLevel(y.getInt("xp-level",0));p.setExp((float)y.getDouble("xp-progress",0));p.setFoodLevel(y.getInt("food",20));p.setSaturation((float)y.getDouble("saturation",5));}
    private ItemStack[] items(List<?> list,int length){ItemStack[] a=new ItemStack[length];if(list!=null)for(int i=0;i<Math.min(length,list.size());i++)if(list.get(i) instanceof ItemStack item)a[i]=item;return a;}
    private void switchInventory(Player p,boolean entering){UUID id=p.getUniqueId();boolean current=smpInventory.contains(id);if(current==entering)return;saveInventory(p,current);loadInventory(p,entering);if(entering)smpInventory.add(id);else smpInventory.remove(id);}
    private long remaining(UUID id){return Math.max(0,bans.getOrDefault(id,0L)-System.currentTimeMillis());}
    private String duration(long ms){long s=Math.max(0,ms/1000);return String.format("%dd %02du %02dm %02ds",s/86400,s/3600%24,s/60%60,s%60);}
    // A command block underneath a pressure plate can invoke this command.
    // Only players actually standing immediately above that block qualify.
    private void enterFromPlate(CommandSender sender) {
        if (!(sender instanceof BlockCommandSender blockSender)) {
            sender.sendMessage("§cGebruik /nlsmp joinplate uitsluitend vanuit een command block.");
            return;
        }
        Location base = blockSender.getBlock().getLocation();
        Player target = null;
        double closest = Double.MAX_VALUE;
        for (Player candidate : base.getWorld().getPlayers()) {
            Location at = candidate.getLocation();
            double dx = at.getX() - (base.getBlockX() + 0.5);
            double dz = at.getZ() - (base.getBlockZ() + 0.5);
            double dy = at.getY() - (base.getBlockY() + 1.0);
            // The player must be over the command block, not just nearby.
            if (Math.abs(dx) > 0.8 || Math.abs(dz) > 0.8 || dy < -0.2 || dy > 2.6) continue;
            double distance = dx * dx + dz * dz + dy * dy;
            if (distance < closest) { closest = distance; target = candidate; }
        }
        if (target != null) enter(target);
    }
    private void enter(Player p) {
        if (wiping) {p.sendMessage("§cSMP wordt momenteel gereset.");return;}
        long r = remaining(p.getUniqueId());
        if (r > 0) {p.sendMessage("§cHardcore cooldown: " + duration(r));return;}
        Location loc = last.get(p.getUniqueId());
        if (!safeSmpLocation(loc)) {
            last.remove(p.getUniqueId());
            loc = start();
        }
        if (loc == null) {p.sendMessage("§cGeen veilige SMP-spawn gevonden. Neem contact op met een admin.");return;}
        if (p.teleport(loc)) {
            p.setFallDistance(0);
            p.setNoDamageTicks(100);
        } else p.sendMessage("§cTeleporteren naar SMP mislukt.");
    }
    private void tick(){if(wiping)return;long now=System.currentTimeMillis();for(Player p:Bukkit.getOnlinePlayers())if(isSmp(p.getWorld())){UUID id=p.getUniqueId();last.put(id,p.getLocation().clone());survival.merge(id,30L,Long::sum);}long left=seasonEnd-now;if(left<=0){if(getConfig().getBoolean("automatic-season-wipe", false))beginWipe(false);return;}long seconds=left/1000;for(long warn:List.of(86400L,3600L,600L,60L))if(seconds<=warn && seconds> warn-31)Bukkit.broadcastMessage("§6[SMP] Seizoen eindigt over "+duration(left));saveState();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){Player p=e.getPlayer();if(e.getTo()==null)return;boolean from=isSmp(e.getFrom().getWorld()),to=isSmp(e.getTo().getWorld());if(!from && to && (wiping||remaining(p.getUniqueId())>0)){e.setCancelled(true);p.sendMessage("§cSMP gesloten. Cooldown: "+duration(remaining(p.getUniqueId())));return;}if(from)last.put(p.getUniqueId(),e.getFrom().clone());if(from!=to){boolean target=to;Bukkit.getScheduler().runTask(this,()->{if(p.isOnline()&&isSmp(p.getWorld())==target)switchInventory(p,target);});}}
    @EventHandler public void portal(PlayerPortalEvent e){if(e.getTo()==null)return;World from=e.getFrom().getWorld();if(!isSmp(from))return;World to=e.getTo().getWorld();if(to.getEnvironment()==World.Environment.NETHER && !to.getName().equals(nether())){World n=Bukkit.getWorld(nether());if(n!=null)e.setTo(new Location(n,e.getFrom().getX()/8,80,e.getFrom().getZ()/8));}else if(to.getEnvironment()==World.Environment.THE_END && !to.getName().equals(end())){World n=Bukkit.getWorld(end());if(n!=null)e.setTo(n.getSpawnLocation());}}
    @EventHandler public void join(PlayerJoinEvent e){Player p=e.getPlayer();if(isSmp(p.getWorld())){smpInventory.add(p.getUniqueId());if(remaining(p.getUniqueId())>0){Location h=hub();if(h!=null)p.teleport(h);}}}
    @EventHandler public void quit(PlayerQuitEvent e){Player p=e.getPlayer();if(isSmp(p.getWorld()))last.put(p.getUniqueId(),p.getLocation().clone());saveInventory(p,smpInventory.contains(p.getUniqueId()));saveState();}
    @EventHandler public void death(PlayerDeathEvent e){Player p=e.getEntity();if(!isSmp(p.getWorld()))return;UUID id=p.getUniqueId();deaths.merge(id,1,Integer::sum);bans.put(id,System.currentTimeMillis()+Math.max(1,getConfig().getInt("hardcore-cooldown-hours",24))*3600000L);last.remove(id);e.getDrops().clear();e.setDroppedExp(0);Bukkit.broadcastMessage("§4☠ §c"+p.getName()+" is gestorven in SMP! §7Cooldown: 24 uur.");saveState();}
    @EventHandler public void respawn(PlayerRespawnEvent e){Player p=e.getPlayer();if(remaining(p.getUniqueId())<=0)return;Location h=hub();if(h!=null)e.setRespawnLocation(h);Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline()&&!isSmp(p.getWorld()))switchInventory(p,false);},2L);}
    @EventHandler(ignoreCancelled=true) public void kill(org.bukkit.event.entity.EntityDeathEvent e){if(e.getEntity() instanceof Player victim && isSmp(victim.getWorld()) && victim.getKiller()!=null)kills.merge(victim.getKiller().getUniqueId(),1,Integer::sum);}
    @EventHandler(ignoreCancelled=true) public void pick(PlayerAttemptPickupItemEvent e){Player p=e.getPlayer();if(!isSmp(p.getWorld()))return;Material m=e.getItem().getItemStack().getType();if(m!=Material.TOTEM_OF_UNDYING && m!=Material.ENDER_PEARL)return;int max=m==Material.TOTEM_OF_UNDYING?getConfig().getInt("limits.totems",1):getConfig().getInt("limits.ender-pearls",64);if(count(p,m)>=max){e.setCancelled(true);p.sendMessage("§cJe hebt het maximum aan "+m.name()+" bereikt.");}}
    private int count(Player p,Material m){int n=0;for(ItemStack it:p.getInventory().getContents())if(it!=null&&it.getType()==m)n+=it.getAmount();for(ItemStack it:p.getEnderChest().getContents())if(it!=null&&it.getType()==m)n+=it.getAmount();return n;}
    @EventHandler public void inv(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!isSmp(p.getWorld()))return;Bukkit.getScheduler().runTask(this,()->enforce(p));}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&isSmp(p.getWorld()))Bukkit.getScheduler().runTask(this,()->enforce(p));}
    @EventHandler public void interact(PlayerInteractEvent e){if(isSmp(e.getPlayer().getWorld()))Bukkit.getScheduler().runTask(this,()->enforce(e.getPlayer()));}
    private void enforce(Player p){if(!p.isOnline()||!isSmp(p.getWorld()))return;for(Material m:List.of(Material.TOTEM_OF_UNDYING,Material.ENDER_PEARL)){int excess=count(p,m)-(m==Material.TOTEM_OF_UNDYING?getConfig().getInt("limits.totems",1):getConfig().getInt("limits.ender-pearls",64));if(excess<=0)continue;for(Inventory inv:List.of(p.getEnderChest(),p.getInventory()))for(int i=inv.getSize()-1;i>=0&&excess>0;i--){ItemStack it=inv.getItem(i);if(it==null||it.getType()!=m)continue;int take=Math.min(excess,it.getAmount());it.setAmount(it.getAmount()-take);if(it.getAmount()==0)inv.setItem(i,null);excess-=take;}p.sendMessage("§cOverschot aan "+m.name()+" verwijderd (ook uit enderchest).");}}
    // Async chunk generation prevents supply drops from depending on online players' loaded chunks.
    // Only one search can run at a time; Bukkit block access always stays on the main thread.
    private boolean dropSearchRunning = false;
    private void spawnDrop() {
        if (wiping || dropSearchRunning || !getConfig().getBoolean("supply-drops.enabled", true)) return;
        World w = Bukkit.getWorld(overworld());
        if (w == null) return;
        dropSearchRunning = true;
        searchDrop(w, 0, Math.max(1, getConfig().getInt("supply-drops.attempts", 35)));
    }
    private void searchDrop(World w, int attempt, int maxAttempts) {
        if (!isEnabled() || wiping || attempt >= maxAttempts) {
            dropSearchRunning = false;
            if (isEnabled() && !wiping && attempt >= maxAttempts)
                getLogger().warning("No safe location found for supply drop after " + maxAttempts + " attempts.");
            return;
        }
        Random r = ThreadLocalRandom.current();
        int min = Math.max(0, getConfig().getInt("supply-drops.minimum-radius", 200));
        int max = Math.max(min + 1, getConfig().getInt("supply-drops.maximum-radius", 4500));
        int x = r.nextInt(-max, max + 1), z = r.nextInt(-max, max + 1);
        if ((Math.abs(x) < min && Math.abs(z) < min)
                || !w.getWorldBorder().isInside(new Location(w, x + 0.5, 80, z + 0.5))) {
            Bukkit.getScheduler().runTask(this, () -> searchDrop(w, attempt + 1, maxAttempts));
            return;
        }
        // Paper loads or generates the selected chunk asynchronously, even when no players are nearby.
        w.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, error) -> {
            if (!isEnabled()) return;
            Bukkit.getScheduler().runTask(this, () -> {
                if (wiping || error != null || chunk == null) {
                    if (error != null) getLogger().warning("Supply-drop chunk load failed: " + error.getMessage());
                    searchDrop(w, attempt + 1, maxAttempts);
                    return;
                }
                Block ground = w.getHighestBlockAt(x, z);
                Block target = ground.getRelative(0, 1, 0);
                if (!ground.getType().isSolid() || ground.isLiquid() || ground.getY() < 60
                        || !target.getType().isAir() || !target.getRelative(0, 1, 0).getType().isAir()) {
                    searchDrop(w, attempt + 1, maxAttempts);
                    return;
                }
                target.setType(Material.CHEST);
                Chest chest = (Chest) target.getState();
                boolean god = ThreadLocalRandom.current().nextDouble()
                        < getConfig().getDouble("supply-drops.god-tier-chance", 0.01);
                fillChest(chest.getBlockInventory(), god);
                chest.update();
                Location location = target.getLocation();
                drops.add(location);
                dropSearchRunning = false;
                Bukkit.broadcastMessage("§6[SMP] " + (god ? "§dGODTIER " : "")
                        + "§eSupply drop op X:" + x + " Z:" + z + "!");
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (drops.remove(location) && location.getBlock().getType() == Material.CHEST)
                        location.getBlock().setType(Material.AIR);
                }, 20L * 60 * Math.max(1, getConfig().getInt("supply-drops.despawn-minutes", 45)));
            });
        });
    }
    private void fillChest(Inventory inv,boolean god){Random r=ThreadLocalRandom.current();Material[] common={Material.DIAMOND,Material.IRON_INGOT,Material.GOLDEN_APPLE,Material.EXPERIENCE_BOTTLE,Material.ENDER_PEARL,Material.NETHERITE_SCRAP};Material[] rare={Material.NETHERITE_INGOT,Material.ENCHANTED_GOLDEN_APPLE,Material.DIAMOND_BLOCK,Material.NETHER_STAR,Material.ELYTRA,Material.TOTEM_OF_UNDYING};for(int i=0;i<(god?8:5);i++){Material m=(god?rare:common)[r.nextInt(god?rare.length:common.length)];inv.addItem(new ItemStack(m,m==Material.EXPERIENCE_BOTTLE?16:m==Material.DIAMOND?4:1));}}
    private void beginWipe(boolean manual){if(wiping)return;wiping=true;Bukkit.broadcastMessage("§c[SMP] Seizoenswipe gestart. SMP tijdelijk gesloten.");Location h=hub();if(h==null){getLogger().severe("Cannot wipe without loaded hub world; aborting.");wiping=false;return;}for(Player p:Bukkit.getOnlinePlayers())if(isSmp(p.getWorld())){saveInventory(p,true);p.teleport(h);switchInventory(p,false);}for(String name:allowed){World w=Bukkit.getWorld(name);if(w!=null&&!Bukkit.unloadWorld(w,true)){getLogger().severe("Could not unload "+name+"; wipe aborted.");wiping=false;return;}}Bukkit.getScheduler().runTaskAsynchronously(this,()->{boolean ok=false;try{backupAndDelete();ok=true;}catch(Exception ex){getLogger().severe("Wipe failed: "+ex.getMessage());}boolean success=ok;Bukkit.getScheduler().runTask(this,()->{if(!success){getLogger().severe("Wipe failed; stop server and restore backup before retrying.");getServer().getPluginManager().disablePlugin(this);return;}for(File f:Objects.requireNonNull(playersDir.listFiles()))if(f.getName().endsWith("-smp.yml"))f.delete();bans.clear();kills.clear();deaths.clear();survival.clear();last.clear();joined.clear();drops.clear();state.set("players",null);seasonEnd=System.currentTimeMillis()+days()*86400000L;saveState();for(String name:allowed){World.Environment env=name.equals(nether())?World.Environment.NETHER:name.equals(end())?World.Environment.THE_END:World.Environment.NORMAL;Bukkit.createWorld(new WorldCreator(name).environment(env).generateStructures(true));}configureWorlds();wiping=false;Bukkit.broadcastMessage("§a[SMP] Nieuw seizoen gestart! Gebruik /nlsmp join.");});});}
    private void backupAndDelete() throws IOException {Path root=Bukkit.getWorldContainer().toPath().toRealPath();Path backups=new File(getDataFolder(),getConfig().getString("backup.directory","smp-season-backups")).toPath().toAbsolutePath().normalize();Files.createDirectories(backups);Path zip=backups.resolve("season-"+System.currentTimeMillis()+".zip");if(getConfig().getBoolean("backup.enabled",true)){try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){for(String name:allowed){Path dir=root.resolve(name).normalize();if(!dir.getParent().equals(root)||Files.isSymbolicLink(dir))throw new IOException("Unsafe world path "+dir);if(!Files.isDirectory(dir))continue;try(var walk=Files.walk(dir)){for(Path file:walk.filter(Files::isRegularFile).toList()){if(Files.isSymbolicLink(file))throw new IOException("Symlink in world");out.putNextEntry(new ZipEntry(name+"/"+dir.relativize(file).toString().replace('\\','/')));Files.copy(file,out);out.closeEntry();}}}}}for(String name:allowed){Path dir=root.resolve(name).normalize();if(!dir.getParent().equals(root)||Files.isSymbolicLink(dir))throw new IOException("Unsafe world path");if(Files.exists(dir))try(var walk=Files.walk(dir)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList()){if(Files.isSymbolicLink(p))throw new IOException("Symlink in world");Files.delete(p);}}}if(getConfig().getBoolean("backup.enabled",true)){try(var list=Files.list(backups)){List<Path> all=list.filter(p->p.getFileName().toString().matches("season-[0-9]+\\.zip")).sorted(Comparator.reverseOrder()).toList();for(int i=Math.max(1,getConfig().getInt("backup.keep",3));i<all.size();i++)Files.deleteIfExists(all.get(i));}}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(args.length==0){sender.sendMessage("§e/nlsmp join | leave | season | top | status | sethub | wipe confirm | drop | cooldown <player> clear");return true;}String sub=args[0].toLowerCase(Locale.ROOT);if(sub.equals("season")){sender.sendMessage("§6SMP seizoen eindigt over §e"+duration(seasonEnd-System.currentTimeMillis()));return true;}if(sub.equals("top")){sender.sendMessage("§6SMP leaderboard (kills / deaths / overleving):");union().stream().sorted(Comparator.comparingInt((UUID u)->kills.getOrDefault(u,0)).reversed()).limit(10).forEach(u->sender.sendMessage("§e"+Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName()).orElse(u.toString().substring(0,8))+" §7"+kills.getOrDefault(u,0)+" / "+deaths.getOrDefault(u,0)+" / "+duration(survival.getOrDefault(u,0L)*1000)));return true;}if(sub.equals("joinplate")){enterFromPlate(sender);return true;}if(sub.equals("join")&&sender instanceof Player p){enter(p);return true;}if(sub.equals("leave")&&sender instanceof Player p){if(!isSmp(p.getWorld())){p.sendMessage("§cJe bent niet in SMP.");return true;}Location h=hub();if(h!=null)p.teleport(h);return true;}if(!sender.hasPermission("nluniverse.smp.admin")){sender.sendMessage("§cGeen toestemming.");return true;}switch(sub){case "status"->sender.sendMessage("§aNLUniverseSMP §7| wipe="+wiping+" | season="+duration(seasonEnd-System.currentTimeMillis())+" | worlds="+allowed);case "sethub"->{if(sender instanceof Player p){getConfig().set("hub.world",p.getWorld().getName());getConfig().set("hub.x",p.getLocation().getX());getConfig().set("hub.y",p.getLocation().getY());getConfig().set("hub.z",p.getLocation().getZ());getConfig().set("hub.yaw",p.getLocation().getYaw());getConfig().set("hub.pitch",p.getLocation().getPitch());saveConfig();sender.sendMessage("§aHub spawn opgeslagen.");}}case "drop"->{spawnDrop();sender.sendMessage("§aSupply-drop poging uitgevoerd.");}case "wipe"->{if(args.length==2&&args[1].equalsIgnoreCase("confirm"))beginWipe(true);else sender.sendMessage("§cWAARSCHUWING: /nlsmp wipe confirm reset alle SMP-werelden en SMP-inventories!");}case "cooldown"->{if(args.length==3&&args[2].equalsIgnoreCase("clear")){OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);bans.remove(target.getUniqueId());saveState();sender.sendMessage("§aCooldown gewist voor "+args[1]);}else sender.sendMessage("§e/nlsmp cooldown <player> clear");}default->sender.sendMessage("§cOnbekend commando.");}return true;}
}
