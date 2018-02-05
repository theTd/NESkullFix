package cn.mineclay.NESkullFix;

import com.flowpowered.nbt.*;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;
import net.minecraft.server.RegionFileCache;

import java.io.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {
    private final static Logger LOGGER = Logger.getLogger("main");

    public static void main(String[] args) {
        if (args.length != 1) {
            LOGGER.warning("1 argument are accepted");
            return;
        }

        File currentPath = new File(args[0]).getAbsoluteFile();
        if (currentPath.exists() && currentPath.isDirectory()) {
            processPath(currentPath);
        } else {
            LOGGER.warning("not a directory");
        }
    }

    private static void processPath(File file) {
        File[] r = file.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.getName().equals("level.dat");
            }
        });
        if (r != null && r.length == 1) {
            // is world directory
            LOGGER.info("found world at " + file);
            int fixed;
            try {
                fixed = processWorld(file);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "error processing world " + file, e);
                return;
            }
            LOGGER.info("fixed " + fixed + " skull(s) in " + file);
        } else {
            File[] dirs = file.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.isDirectory();
                }
            });

            if (dirs == null) return;
            for (File dir : dirs) processPath(dir);
        }
    }

    private static int processWorld(File worldPath) throws IOException {
        int fixed = 0;
        File regionPath = new File(worldPath, "region");
        File[] ls = regionPath.listFiles();
        if (ls != null) {
            for (File l : ls) {
                int[] scale;
                try {
                    scale = getXYScale(l.getName());
                } catch (Exception e) {
                    LOGGER.severe("error processing file " + l + ": " + e.getMessage());
                    continue;
                }

                for (int x = scale[0]; x <= scale[1]; x++) {
                    for (int y = scale[2]; y <= scale[3]; y++) {
                        DataInputStream dataIn = RegionFileCache.c(worldPath, x, y);
                        if (dataIn == null) continue;
                        NBTInputStream nbtIn = new NBTInputStream(dataIn, false);
                        Tag tag = nbtIn.readTag();
                        nbtIn.close();

                        CompoundTag levelTagCompound = (CompoundTag) ((CompoundTag) tag).getValue().get("Level");
                        //noinspection unchecked
                        ListTag<CompoundTag> tileEntitiesListTag = (ListTag<CompoundTag>) levelTagCompound.getValue().get("TileEntities");

                        boolean changed = false;
                        for (CompoundTag compoundTag : tileEntitiesListTag.getValue()) {
                            if (!isPlayerSkull(compoundTag)) continue;
                            CompoundTag ownerTag = (CompoundTag) compoundTag.getValue().get("Owner");
                            if (ownerTag == null) continue;
                            ownerTag.getValue().put(new StringTag("Id", UUID.nameUUIDFromBytes(
                                    UUID.randomUUID().toString().getBytes()
                            ).toString()));
                            ownerTag.getValue().put(new StringTag("Name", "clay_skull_fix"));
                            fixed++;
                            changed = true;
                        }

                        if (changed) {
                            DataOutputStream dataOut = RegionFileCache.d(worldPath, x, y);
                            NBTOutputStream nbtOut = new NBTOutputStream(dataOut, false);
                            nbtOut.writeTag(tag);
                            nbtOut.close();
                        }
                    }
                }
            }
        }
        return fixed;
    }

    /**
     * @param filename
     * @return xStart, xEnd, yStart, yEnd
     */
    private static int[] getXYScale(String filename) {
        if (!filename.startsWith("r.")) throw new IllegalArgumentException("wrong filename");
        if (!filename.endsWith(".mca")) throw new IllegalArgumentException("wrong filename");
        filename = filename.substring(2, filename.length() - 4);
        int[] rtn = new int[4];
        int dot = filename.indexOf(".");
        rtn[0] = Integer.parseInt(filename.substring(0, dot)) * 32;
        rtn[1] = rtn[0] + 31;
        rtn[2] = Integer.parseInt(filename.substring(dot + 1)) * 32;
        rtn[3] = rtn[2] + 31;
        return rtn;
    }

    private static boolean isSkull(CompoundTag tag) {
        if (tag == null) return false;
        Tag t = tag.getValue().get("id");
        return t != null && t instanceof StringTag && "Skull".equals(((StringTag) t).getValue());
    }

    private static boolean isPlayerSkull(CompoundTag tag) {
        if (tag == null) return false;
        Tag t = tag.getValue().get("SkullType");
        return t != null && t instanceof ByteTag && ((ByteTag) t).getValue() == (byte) 3;
    }

    private static boolean isHeadDBSkull(CompoundTag tag) {
        if (tag == null) return false;
        Tag ownerTag = tag.getValue().get("Owner");
        if (ownerTag == null || !(ownerTag instanceof CompoundTag)) return false;
        CompoundTag ownerCompoundTag = (CompoundTag) ownerTag;
        Tag propertiesTag = ownerCompoundTag.getValue().get("Properties");
        if (propertiesTag == null || !(propertiesTag instanceof CompoundTag)) return false;
        CompoundTag propertiesCompoundTag = (CompoundTag) propertiesTag;
        Tag texturesTag = propertiesCompoundTag.getValue().get("textures");
        if (texturesTag == null || !(texturesTag instanceof ListTag)) return false;
        //noinspection unchecked
        ListTag<CompoundTag> texturesListTag = (ListTag<CompoundTag>) texturesTag;
        for (CompoundTag compoundTag : texturesListTag.getValue()) {
            if (compoundTag.getValue().get("Signature") instanceof StringTag && ((StringTag) compoundTag.getValue().get("Signature")).getValue().equals("signed"))
                return true;
        }
        return false;
    }
}
