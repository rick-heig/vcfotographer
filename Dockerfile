FROM ubuntu:20.04

# This is a Dockerfile for running VCFotographer : https://github.com/rick-heig/vcfotographer
MAINTAINER Rick Wertenbroek <rick.wertenbroek@heig-vd.ch>

ENV DEBIAN_FRONTEND noninteractive

#ARG SBT_VERSION=1.3.4
ARG OPENJDK_VERSION=11

# Install required software and clean as not to make the layer dirty
RUN apt-get update && apt-get -y upgrade && apt-get install -y \
	apt-utils curl gnupg apt-transport-https && \
	apt-get clean && apt-get purge && \
	rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
        
# Add SBT package to manager
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import && \
    chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg

# Install required software and clean as not to make the layer dirty
RUN apt-get update && apt-get -y upgrade && apt-get install -y \
	wget xvfb software-properties-common default-jre unzip glib-networking-common \
        sbt openjdk-$OPENJDK_VERSION-jdk && \
	apt-get clean && apt-get purge && \
	rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Install IGV and change launching script so that it can use up to 8GB of RAM
RUN mkdir -p /usr/src/igv && \
	mkdir -p /usr/src/work && \
	cd /usr/src/igv && \
        wget https://data.broadinstitute.org/igv/projects/downloads/2.7/IGV_Linux_2.7.2.zip && \
        unzip IGV_Linux_*.zip && \
        cd IGV_Linux_* && \
        sed -i 's/Xmx[0-9]*g/Xmx8g/' igv.sh && \
        ln -s $(realpath .)/igv.sh /usr/bin/igv

# Give rwx rights to a temporary work directory for IGV to put it's temporary data
RUN mkdir -p /tmp/work && chmod 777 /tmp/work

# Give rwx rights to the .cache directory to everyone, otherwise dconf will complain when the user is not root
RUN mkdir -p /.cache && chmod 777 /.cache

# Install VCFotographer

# Add github key so git clone does not complain
RUN mkdir /root/.ssh && \
    ssh-keyscan github.com >> /root/.ssh/known_hosts

# Clone VCFotographer Source Code and build fat jar plus wrapper execution script
RUN cd /usr/src/ && \
    git clone https://github.com/rick-heig/vcfotographer.git && \
    cd /usr/src/vcfotographer/vcfotographer/ && \
    sbt assembly && \
    cp target/scala*/VCFotographer*.jar /usr/local/ && \
    echo '#!/bin/bash' > vcfotographer && \
    echo 'vgi > /dev/null 2>&1 &' >> vcfotographer && \
    echo 'sleep 10 # Dirty way of waiting for IGV to be ready TODO change this (expect)' >> vcfotographer && \
    echo 'java -jar /usr/local/VCFotographer*.jar "$@"' >> vcfotographer && \
    chmod +x vcfotographer && \
    cp vcfotographer /usr/local/bin/ && \
    cd /usr/src/ && \
    rm -r vcfotographer

# Work in this temporary directory
WORKDIR /tmp/work

# Create a dummy executable for docker usage
RUN cd /usr/local/bin && \
    echo '#!/bin/bash' > usage && \
    echo 'echo Usage: ' >> usage && \
    chmod +x usage

# Create a script to launch IGV with a virtual frame buffer
RUN cd /usr/local/bin && \
    echo '#!/bin/bash' > vgi && \
    echo 'xvfb-run --server-args="-screen 0 1920x1200x24" -a igv' >> vgi && \
    chmod +x vgi
    

# Launch IGV with a virtual frame buffer (resolution is not really important but colordepth is)
# TODO : Replace with script
#CMD xvfb-run --server-args="-screen 0 1920x1200x24" -a igv
CMD vcfotographer

# Possible run command :
# docker run --user $(id -u):$(id -g) -v <input_directory>:/usr/input:ro -v <output_directory:/usr/output rwe/vcfotographer <args to vcfotographer>
#
# Running IGV in graphic mode (Linux only), uses the host X server, can be done in Windows / Mac OS X but you are own your own as to how to manage your X server
# docker run -it --user $(id -u):$(id -g) --net=host --env="DISPLAY" --volume="$HOME/.Xauthority:/root/.Xauthority:ro" --rm -v$(pwd):/usr/src/work rwe/vcfotographer igv

# docker run --user $(id -u):$(id -g) -p <HostPort>:60151 --rm -v $(pwd):/home/working -w /home/working <IMAGE_NAME>
#
# The user id and group id are passed so that IGV can create files on the volume with the same user as the one calling
# This avoids having "root owned" files generated in the directory mounted as a volume.

# IGV Documentation : http://software.broadinstitute.org/software/igv/home
# IGV Network command documentation : https://software.broadinstitute.org/software/igv/PortCommands