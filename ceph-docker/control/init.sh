#!/bin/sh

: "${SSH_PRIVATE_KEY?SSH_PRIVATE_KEY is empty, please use up.sh}"
: "${SSH_PUBLIC_KEY?SSH_PUBLIC_KEY is empty, please use up.sh}"

if [ ! -f ~/.ssh/known_hosts ]; then
    mkdir -m 700 ~/.ssh
    echo $SSH_PRIVATE_KEY | perl -p -e 's/↩/\n/g' > ~/.ssh/id_rsa
    chmod 600 ~/.ssh/id_rsa
    echo $SSH_PUBLIC_KEY > ~/.ssh/id_rsa.pub
    echo > ~/.ssh/known_hosts
    for f in $(seq 1 3);do
	ssh-keyscan -t rsa m$f >> ~/.ssh/known_hosts
        sshpass -p root ssh-copy-id root@m$f
    done
    for f in $(seq 1 4);do
	ssh-keyscan -t rsa o$f >> ~/.ssh/known_hosts
        sshpass -p root ssh-copy-id root@o$f
    done
    for f in $(seq 1 2);do
	ssh-keyscan -t rsa n$f >> ~/.ssh/known_hosts
        sshpass -p root ssh-copy-id root@n$f
    done
fi

# TODO: assert that SSH_PRIVATE_KEY==~/.ssh/id_rsa

cat <<EOF 
Welcome to Jepsen ceph on Docker
===========================

Please run \`docker exec -it ceph-control bash\` in another terminal to proceed.
EOF

# hack for keep this container running
tail -f /dev/null
