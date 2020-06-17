/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.logging;

import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.FOLDER;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.PREDICTED_HOTSEAT_CONTAINER;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__ALLAPPS;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__BACKGROUND;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__HOME;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__OVERVIEW;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__SRC_STATE__HOME;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.LogConfig;
import com.android.systemui.shared.system.SysUiStatsLog;

import java.util.ArrayList;
import java.util.OptionalInt;

/**
 * This class calls StatsLog compile time generated methods.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 * $ wwdebug (to turn on the logcat printout)
 * $ wwlogcat (see logcat with grep filter on)
 * $ statsd_testdrive (see how ww is writing the proto to statsd buffer)
 */
public class StatsLogCompatManager extends StatsLogManager {

    private static final String TAG = "StatsLog";
    private static final boolean IS_VERBOSE = Utilities.isPropertyEnabled(LogConfig.STATSLOG);

    private static Context sContext;

    private static final InstanceId DEFAULT_INSTANCE_ID = InstanceId.fakeInstanceId(0);
    // LauncherAtom.ItemInfo.getDefaultInstance() should be used but until launcher proto migrates
    // from nano to lite, bake constant to prevent robo test failure.
    private static final int DEFAULT_PAGE_INDEX = -2;
    private static final int FOLDER_HIERARCHY_OFFSET = 100;

    public StatsLogCompatManager(Context context) {
        sContext = context;
    }

    @Override
    public StatsLogger logger() {
        return new StatsCompatLogger();
    }

    /**
     * Logs an event.
     *
     * @param event an enum implementing EventEnum interface.
     * @param atomInfo item typically containing app or task launch related information.
     */
    public void log(EventEnum event, InstanceId instanceId, LauncherAtom.ItemInfo atomInfo) {
        LauncherAppState.getInstance(sContext).getModel().enqueueModelUpdateTask(
                new BaseModelUpdateTask() {
                    @Override
                    public void execute(LauncherAppState app, BgDataModel dataModel,
                            AllAppsList apps) {
                        write(event, instanceId, atomInfo, LAUNCHER_UICHANGED__DST_STATE__HOME,
                                LAUNCHER_UICHANGED__DST_STATE__BACKGROUND, OptionalInt.empty());
                    }
                });
    }

    /**
     * Logs an event.
     *
     * @param event an enum implementing EventEnum interface.
     * @param atomItemInfo item typically containing app or task launch related information.
     */
    @Override
    public void log(EventEnum event, @Nullable LauncherAtom.ItemInfo atomItemInfo, int srcState,
            int dstState) {
        write(event, DEFAULT_INSTANCE_ID,
                atomItemInfo == null ? LauncherAtom.ItemInfo.getDefaultInstance() : atomItemInfo,
                srcState, dstState, OptionalInt.empty());
    }

    /**
     * Logs a ranking event and accompanying {@link InstanceId} and package name.
     */
    @Override
    public void log(EventEnum rankingEvent, InstanceId instanceId, @Nullable String packageName,
            int position) {
        SysUiStatsLog.write(SysUiStatsLog.RANKING_SELECTED,
                rankingEvent.getId() /* event_id = 1; */,
                packageName /* package_name = 2; */,
                instanceId.getId() /* instance_id = 3; */,
                position /* position_picked = 4; */);
    }

    private  void write(EventEnum event, InstanceId instanceId,
            LauncherAtom.ItemInfo atomInfo,
            int srcState, int dstState, OptionalInt mRank) {
        if (IS_VERBOSE) {
            String name = (event instanceof Enum) ? ((Enum) event).name() :
                    event.getId() + "";

            Log.d(TAG, instanceId == DEFAULT_INSTANCE_ID
                    ? String.format("\n%s (State:%s->%s)\n%s", name, getStateString(srcState),
                    getStateString(dstState),  atomInfo)
                    : String.format("\n%s (State:%s->%s) (InstanceId:%s)\n%s", name,
                            getStateString(srcState), getStateString(dstState), instanceId,
                            atomInfo));
        }

        SysUiStatsLog.write(
                SysUiStatsLog.LAUNCHER_EVENT,
                SysUiStatsLog.LAUNCHER_UICHANGED__ACTION__DEFAULT_ACTION /* deprecated */,
                srcState,
                dstState,
                null /* launcher extensions, deprecated */,
                false /* quickstep_enabled, deprecated */,
                event.getId() /* event_id */,
                atomInfo.getItemCase().getNumber() /* target_id */,
                instanceId.getId() /* instance_id TODO */,
                0 /* uid TODO */,
                getPackageName(atomInfo) /* package_name */,
                getComponentName(atomInfo) /* component_name */,
                getGridX(atomInfo, false) /* grid_x */,
                getGridY(atomInfo, false) /* grid_y */,
                getPageId(atomInfo, false) /* page_id */,
                getGridX(atomInfo, true) /* grid_x_parent */,
                getGridY(atomInfo, true) /* grid_y_parent */,
                getPageId(atomInfo, true) /* page_id_parent */,
                getHierarchy(atomInfo) /* hierarchy */,
                atomInfo.getIsWork() /* is_work_profile */,
                mRank.orElse(atomInfo.getRank()) /* rank */,
                atomInfo.getFolderIcon().getFromLabelState().getNumber() /* fromState */,
                atomInfo.getFolderIcon().getToLabelState().getNumber() /* toState */,
                atomInfo.getFolderIcon().getLabelInfo() /* edittext */,
                getCardinality(atomInfo) /* cardinality */);
    }

    /**
     * Logs the workspace layout information on the model thread.
     */
    @Override
    public void logSnapshot() {
        LauncherAppState.getInstance(sContext).getModel().enqueueModelUpdateTask(
                new SnapshotWorker());
    }

    private class SnapshotWorker extends BaseModelUpdateTask {
        private final InstanceId mInstanceId;
        SnapshotWorker() {
            mInstanceId = new InstanceIdSequence(
                    1 << 20 /*InstanceId.INSTANCE_ID_MAX*/).newInstanceId();
        }

        @Override
        public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
            IntSparseArrayMap<FolderInfo> folders = dataModel.folders.clone();
            ArrayList<ItemInfo> workspaceItems = (ArrayList) dataModel.workspaceItems.clone();
            ArrayList<LauncherAppWidgetInfo> appWidgets = (ArrayList) dataModel.appWidgets.clone();
            for (ItemInfo info : workspaceItems) {
                LauncherAtom.ItemInfo atomInfo = info.buildProto(null);
                writeSnapshot(atomInfo, mInstanceId);
            }
            for (FolderInfo fInfo : folders) {
                for (ItemInfo info : fInfo.contents) {
                    LauncherAtom.ItemInfo atomInfo = info.buildProto(fInfo);
                    writeSnapshot(atomInfo, mInstanceId);
                }
            }
            for (ItemInfo info : appWidgets) {
                LauncherAtom.ItemInfo atomInfo = info.buildProto(null);
                writeSnapshot(atomInfo, mInstanceId);
            }
        }
    }

    private static void writeSnapshot(LauncherAtom.ItemInfo info, InstanceId instanceId) {
        if (IS_VERBOSE) {
            Log.d(TAG, String.format("\nwriteSnapshot(%d):\n%s", instanceId.getId(), info));
        }
        if (!Utilities.ATLEAST_R) {
            return;
        }
        SysUiStatsLog.write(SysUiStatsLog.LAUNCHER_SNAPSHOT,
                0 /* event_id */,
                info.getItemCase().getNumber() /* target_id */,
                instanceId.getId() /* instance_id */,
                0 /* uid */,
                getPackageName(info) /* package_name */,
                getComponentName(info) /* component_name */,
                getGridX(info, false) /* grid_x */,
                getGridY(info, false) /* grid_y */,
                getPageId(info, false) /* page_id */,
                getGridX(info, true) /* grid_x_parent */,
                getGridY(info, true) /* grid_y_parent */,
                getPageId(info, true) /* page_id_parent */,
                getHierarchy(info) /* hierarchy */,
                info.getIsWork() /* is_work_profile */,
                info.getAttribute().getNumber() /* origin */,
                getCardinality(info) /* cardinality */,
                info.getWidget().getSpanX(),
                info.getWidget().getSpanY());
    }

    /**
     * Helps to construct and write statsd compatible log message.
     */
    private class StatsCompatLogger implements StatsLogger {
        private ItemInfo mItemInfo = new ItemInfo();
        private InstanceId mInstanceId = DEFAULT_INSTANCE_ID;
        private OptionalInt mRank = OptionalInt.empty();
        private int mSrcState = LAUNCHER_UICHANGED__SRC_STATE__HOME;
        private int mDstState = LAUNCHER_UICHANGED__DST_STATE__BACKGROUND;

        @Override
        public StatsLogger withItemInfo(ItemInfo itemInfo) {
            this.mItemInfo = itemInfo;
            return this;
        }

        @Override
        public StatsLogger withInstanceId(InstanceId instanceId) {
            this.mInstanceId = instanceId;
            return this;
        }

        @Override
        public StatsLogger withRank(int rank) {
            this.mRank = OptionalInt.of(rank);
            return this;
        }

        @Override
        public StatsLogger withSrcState(int srcState) {
            this.mSrcState = srcState;
            return this;
        }

        @Override
        public StatsLogger withDstState(int dstState) {
            this.mDstState = dstState;
            return this;
        }

        @Override
        public void log(EventEnum event) {
            if (!Utilities.ATLEAST_R) {
                return;
            }

            if (mItemInfo.container < 0) {
                // Item is not within a folder. Write to StatsLog in same thread.
                write(event, mInstanceId, mItemInfo.buildProto(), mSrcState, mDstState, mRank);
            } else {
                // Item is inside the folder, fetch folder info in a BG thread
                // and then write to StatsLog.
                LauncherAppState.getInstance(sContext).getModel().enqueueModelUpdateTask(
                        new BaseModelUpdateTask() {
                            @Override
                            public void execute(LauncherAppState app, BgDataModel dataModel,
                                    AllAppsList apps) {
                                FolderInfo folderInfo = dataModel.folders.get(mItemInfo.container);
                                write(event, mInstanceId, mItemInfo.buildProto(folderInfo),
                                        mSrcState, mDstState, mRank);
                            }
                        });
            }
        }
    }

    private static int getCardinality(LauncherAtom.ItemInfo info) {
        return info.getContainerInfo().getContainerCase().equals(PREDICTED_HOTSEAT_CONTAINER)
                ? info.getContainerInfo().getPredictedHotseatContainer().getCardinality()
                : info.getFolderIcon().getCardinality();
    }

    private static String getPackageName(LauncherAtom.ItemInfo info) {
        switch (info.getItemCase()) {
            case APPLICATION:
                return info.getApplication().getPackageName();
            case SHORTCUT:
                return info.getShortcut().getShortcutName();
            case WIDGET:
                return info.getWidget().getPackageName();
            case TASK:
                return info.getTask().getPackageName();
            default:
                return null;
        }
    }

    private static String getComponentName(LauncherAtom.ItemInfo info) {
        switch (info.getItemCase()) {
            case APPLICATION:
                return info.getApplication().getComponentName();
            case SHORTCUT:
                return info.getShortcut().getShortcutName();
            case WIDGET:
                return info.getWidget().getComponentName();
            case TASK:
                return info.getTask().getComponentName();
            default:
                return null;
        }
    }

    private static int getGridX(LauncherAtom.ItemInfo info, boolean parent) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            if (parent) {
                return info.getContainerInfo().getFolder().getWorkspace().getGridX();
            } else {
                return info.getContainerInfo().getFolder().getGridX();
            }
        } else {
            return info.getContainerInfo().getWorkspace().getGridX();
        }
    }

    private static int getGridY(LauncherAtom.ItemInfo info, boolean parent) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            if (parent) {
                return info.getContainerInfo().getFolder().getWorkspace().getGridY();
            } else {
                return info.getContainerInfo().getFolder().getGridY();
            }
        } else {
            return info.getContainerInfo().getWorkspace().getGridY();
        }
    }

    private static int getPageId(LauncherAtom.ItemInfo info, boolean parent) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            if (parent) {
                return info.getContainerInfo().getFolder().getWorkspace().getPageIndex();
            } else {
                return info.getContainerInfo().getFolder().getPageIndex();
            }
        } else {
            return info.getContainerInfo().getWorkspace().getPageIndex();
        }
    }

    private static int getHierarchy(LauncherAtom.ItemInfo info) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            return info.getContainerInfo().getFolder().getParentContainerCase().getNumber()
                    + FOLDER_HIERARCHY_OFFSET;
        } else {
            return info.getContainerInfo().getContainerCase().getNumber();
        }
    }

    private static String getStateString(int state) {
        switch (state) {
            case LAUNCHER_UICHANGED__DST_STATE__BACKGROUND:
                return "BACKGROUND";
            case LAUNCHER_UICHANGED__DST_STATE__HOME:
                return "HOME";
            case LAUNCHER_UICHANGED__DST_STATE__OVERVIEW:
                return "OVERVIEW";
            case LAUNCHER_UICHANGED__DST_STATE__ALLAPPS:
                return "ALLAPPS";
            default:
                return "INVALID";

        }
    }
}
