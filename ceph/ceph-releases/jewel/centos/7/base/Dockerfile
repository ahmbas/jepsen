# CEPH BASE IMAGE
# CEPH VERSION: Jewel
# CEPH VERSION DETAIL: 0.94.x

FROM centos:7
#FROM ceph/base:tag-build-master-jewel-centos-7
MAINTAINER gator1 "gator1@github.com"


# Install prerequisites
RUN yum install -y unzip net-tools 

RUN yum install -y openssh-server openssh-clients openssl-libs git sudo
#RUN yum install -y pwgen

RUN useradd -ms /bin/bash ceph
RUN echo "ceph:ceph" | chpasswd
RUN usermod -a -G root ceph
RUN usermod -g wheel ceph

#ADD entrypoint.sh /entrypoint.sh
#ENTRYPOINT ["/entrypoint.sh"]

ADD set_root_pw.sh /set_root_pw.sh
ADD run.sh /run.sh
RUN chmod +x /*.sh

ENV AUTHORIZED_KEYS **None**

RUN mkdir /var/run/sshd
RUN sed -i "s/UsePrivilegeSeparation.*/UsePrivilegeSeparation no/g" /etc/ssh/sshd_config
RUN ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key -N ''
RUN ssh-keygen -t ecdsa -f /etc/ssh/ssh_host_ecdsa_key -N ''
RUN ssh-keygen -t ed25519 -f /etc/ssh/ssh_host_ed25519_key -N ''

#RUN /usr/sbin/sshd -D

EXPOSE 22
CMD ["/run.sh"]

