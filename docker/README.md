Dockerized Jepsen
=================

This docker image attempts to simplify the setup required by Jepsen.
It is intended to be used by a CI tool or anyone with docker who wants to try jepsen themselves.

It contains all the jepsen dependencies and code. It uses [docker-in-docker](https://github.com/jpetazzo/dind) to spin up the five
containers used by Jepsen.  

To start run (note the required --privileged flag)
Got my own with the jepsen latest from gator1, me!

````
    docker run --privileged -t -i gators/jepsen
````

Building the docker image
=========================

Alternatively, you can build the image yourself. This is a multi-step process, mainly because [docker doesn't let you build with --privileged operations](https://github.com/docker/docker/issues/1916)

1.  From this directory run 

    ````
	docker build -t jepsen .
    ````

2.  Start the container and run build-dockerized-jepsen.sh

    ````
    docker run --privileged -t -i jepsen

    > build-dockerized-jepsen.sh
    ````

3.  From another window commit the updated image

    ````
    docker commit {above container-id} jepsen
    ````
    
4.  With the final image created, you can create a container to run Jepsen from

    ```
    docker run --privileged -t -i jepsen
    ```

    install tmux in the image for multiple terminals (kind of)
    sudo apt-get install tmux

    issue:
    tmux

    then resize your window to be double width.

    Then:

    CTRL+b, %

    Then you can use:
    CTRL+b, <- and -> 

    (Left arrow and right arrow)

    to switch windows. 

