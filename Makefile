SOURCE_DIR = /home/beakerx/mtdm/
JUPYTER_DIR ?= notebooks
FULL_JUPYTER_DIR = /home/beakerx/mtdm/$(JUPYTER_DIR)

clean:
	rm -rf target

doc:
	lein codox
jupyter:
	docker build -t mtdm-jupyter .
	docker run -v `pwd`/:/home/beakerx/mtdm \
		-p 8888:8888 -w $(FULL_JUPYTER_DIR) mtdm-jupyter

run-jvm:
	lein run
build-jvm:
	lein uberjar
execute-jvm:
	java -jar target/jvm/uberjar/mtdm-0.1.0-SNAPSHOT-standalone.jar

clr-deps:
	./fix-clojure-clr-linking.sh
run-clr: clr-deps
	lein clr run -m mtdm.core
build-clr: clr-deps
	lein clr compile :all
	cp target/clr/clj/Clojure.1.9.0-alpha15/all/net40/*.dll target/clr/bin
execute-clr: clr-deps
	mono target/clr/bin/mtdm.core.exe

build: clean build-jvm build-clr

test:
	docker run -v `pwd`/:/home/beakerx/mtdm mtdm-jupyter lein test
