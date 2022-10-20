FROM beakerx/beakerx:1.1.0

# Download Lein script
USER root
ADD https://raw.githubusercontent.com/technomancy/leiningen/2.8.1/bin/lein /usr/local/bin/lein
RUN chmod a+rx /usr/local/bin/lein
USER beakerx

# Install lein
RUN /bin/bash -c "source activate beakerx && lein && source deactivate"

# Install Packages
USER root
RUN apt-get update && apt-get install -y \
    gcc \
    graphviz \
    g++ \
    texlive-xetex \
    && rm -rf /var/lib/apt/lists/*
USER beakerx

# Install Clojure dependencies
RUN mkdir -p /home/beakerx/mtdm/target
COPY project.clj /home/beakerx/mtdm/project.clj
RUN /bin/bash -c "source activate beakerx && cd /home/beakerx/mtdm/ && lein deps && source deactivate"

# Install Python dependencies
RUN /opt/conda/envs/beakerx/bin/python -m pip install --upgrade pip
RUN /opt/conda/envs/beakerx/bin/python -m pip install orange3==3.26.0 scipy==1.5.4 scikit-posthocs==0.7.0 cython requests==2.27.1 idna==2.8


# Copy across beakerx.json config
COPY beakerx.json /home/beakerx/.jupyter/beakerx.json

WORKDIR /home/beakerx/mtdm/notebooks
