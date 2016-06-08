/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.sdxl2.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.sdxl2.SdxL2ConnectionPoint;
import org.onosproject.sdxl2.SdxL2Service;
import org.onosproject.sdxl2.SdxL2State;

/**
 * Cli to print the details of an SdxL2ConnectionPoint.
 */
@Command(scope = "sdxl2", name = "sdxl2cp", description = "Prints the details of an SDXL2ConnectionPoint")
public class SdxL2GetCPCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "sdxl2cpname", description = "Sdxl2cp name", required = true, multiValued = false)
    String sdxl2cpname = null;

    private static final String HEADER       = "\n\u001B[1;37mStatus\t\t" +
            "Connection Point\t\tName\t\tVlan IDs\t\tCE Mac Address\u001B[0m";
    private static final String SEPARATOR    = "\u001B[1;37m------------" +
            "-----------------------------------------------------------" +
            "--------------------------------------\u001B[0m";
    private static final String FORMAT_SDXL2CP_ONLINE   = "\u001B[1;32m%" +
            "s\u001B[0m\t\t\u001B[1;37m%s/%s\t\t%s\t\t%s\t\t%s\u001B[0m\n";
    private static final String FORMAT_SDXL2CP_OFFLINE  = "\u001B[1;31m%" +
            "s\u001B[0m\t\t\u001B[1;37m%s/%s\t\t%s\t\t%s\t\t%s\u001B[0m\n";

    @Override
    protected void execute() {
        SdxL2Service sdxl2Service = get(SdxL2Service.class);
        SdxL2ConnectionPoint sdxl2ConnectionPoint = sdxl2Service.getSdxL2ConnectionPoint(sdxl2cpname);
        if (sdxl2ConnectionPoint == null) {
            return;
        }
        SdxL2State sdxl2cpState = sdxl2Service.getEdgePortState(sdxl2ConnectionPoint.connectPoint());
        print(HEADER);
        print(SEPARATOR);
        if (sdxl2cpState == SdxL2State.ONLINE) {
            print(FORMAT_SDXL2CP_ONLINE,
                    "ONLINE",
                    sdxl2ConnectionPoint.connectPoint().elementId(),
                    sdxl2ConnectionPoint.connectPoint().port(),
                    sdxl2ConnectionPoint.name(),
                    sdxl2ConnectionPoint.vlanIds(),
                    sdxl2ConnectionPoint.macAddress());
        } else if (sdxl2cpState == SdxL2State.OFFLINE) {
            print(FORMAT_SDXL2CP_OFFLINE,
                    "OFFLINE",
                    sdxl2ConnectionPoint.connectPoint().elementId(),
                    sdxl2ConnectionPoint.connectPoint().port(),
                    sdxl2ConnectionPoint.name(),
                    sdxl2ConnectionPoint.vlanIds(),
                    sdxl2ConnectionPoint.macAddress());
        }
    }
}
