#!/bin/bash
#
# ============LICENSE_START=======================================================
# ONAP
# ================================================================================
# Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
# ================================================================================
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# ============LICENSE_END=========================================================
#

#
# Script to build a Docker file for the simulators. The docker image
# generated by this script should NOT be placed in the ONAP nexus, it is
# only for testing purposes.
#

if [ -z "$SIMULATOR_HOME" ]
then
    SIMULATOR_HOME=$PWD
fi

# Check for the dockerfile
if [ ! -f "$SIMULATOR_HOME/src/main/package/docker/Dockerfile" ]
then
    echo docker file "$SIMULATOR_HOME/src/main/package/docker/Dockerfile" not found
    exit 1
fi

# Check for the start script
if [ ! -f "$SIMULATOR_HOME/src/main/package/docker/simulators.sh" ]
then
    echo start script "$SIMULATOR_HOME/src/main/package/docker/simulators.sh" not found
    exit 1
fi

# Check for the tarball
tarball_count=`ls $SIMULATOR_HOME/target/policy-models-simulators-*tarball.tar.gz 2> /dev/null | wc | awk '{print $1}'`
if [ "$tarball_count" -ne "1" ]
then
    echo one and only one tarball should exist in the target directory
    exit 2
fi

# Set up the docker build
rm -fr $SIMULATOR_HOME/target/docker
mkdir $SIMULATOR_HOME/target/docker
cp $SIMULATOR_HOME/src/main/package/docker/Dockerfile $SIMULATOR_HOME/target/docker
cp $SIMULATOR_HOME/src/main/package/docker/simulators.sh $SIMULATOR_HOME/target/docker
cp $SIMULATOR_HOME/target/policy-models-simulators-*tarball.tar.gz \
    $SIMULATOR_HOME/target/docker/policy-models-simulators-tarball.tar.gz

# Run the docker build
cd $SIMULATOR_HOME/target
docker build -t policy/simulators docker