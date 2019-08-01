FROM maven:3.5

# install jdk11
RUN echo 'deb http://ftp.debian.org/debian stretch-backports main' > /etc/apt/sources.list.d/backports.list \
  && apt-get update \
	&& apt-get install -y openjdk-11-jdk-headless

# install taurus for integration tests with blazemeter and different jmeter versions
ENV TAURUS_VERSION=1.13.3
RUN apt-get update \
  && apt-get -y install python default-jre-headless python-tk python-pip python-dev libxml2-dev libxslt-dev zlib1g-dev net-tools \
  && pip install bzt==$TAURUS_VERSION

# install ssh client to be able to release code and use git with ssh
RUN apt-get update \
  && apt-get -y install openssh-client

COPY scripts/* /
RUN chmod +x /*.sh
