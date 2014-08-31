#!/bin/bash

export APP_HOME=${0%/*.sh}
cd $APP_HOME

CP=$APP_HOME/config

for DIST in $( ls $APP_HOME/dist/*.jar); do
   CP=$CP:$DIST
done

for LIB in $( ls $APP_HOME/lib/*.jar); do
   CP=$CP:$LIB
done

java -DAPP_HOME=$APP_HOME -cp $CP net.villonanny.VilloNanny -utf8 "$*"
