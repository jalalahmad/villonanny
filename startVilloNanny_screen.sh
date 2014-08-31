#!/bin/bash

# Change this name for multiple installations
NAME="VilloNanny"

SCREEN=`screen -ls | grep $NAME`
# SCREEN=`screen -ls | grep 'No Sockets'`
if [ "$SCREEN" != "" ]; then
        screen -d -r $NAME
        exit 0
fi

export APP_HOME=${0%/*.sh}
cd $APP_HOME

CP=$APP_HOME/config

for DIST in $( ls $APP_HOME/dist/*.jar); do
   CP=$CP:$DIST
done

for LIB in $( ls $APP_HOME/lib/*.jar); do
   CP=$CP:$LIB
done

# CTRL-A CTRL-D to detach; run again to reattach
screen -S $NAME java -DAPP_HOME=$APP_HOME -cp $CP net.villonanny.VilloNanny -utf8 "$*"
