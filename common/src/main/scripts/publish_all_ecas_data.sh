#!/bin/bash
# shell script to publish all ECAS data to LabCAS

data_dir="/labcas-data/ecas"
archive_dir="/usr/local/labcas/backend/archive"
script1="/usr/local/labcas/scripts/delete_collection.sh"
script2="/usr/local/labcas/src/labcas-backend/common/src/main/python/gov/nasa/jpl/edrn/labcas/client/labcas_collection_publisher.py"

# activate Python labcas virtual environment
source /data/local/labcas/labcas_venv/bin/activate

# loop over all collections = datasets
for source_dir in $data_dir/* ; do
    echo ""
	
    # extract dataset name = subdir name without the path
    dataset=$(basename "$source_dir")
    
    # 1) create symlinks from $LABCAS_ARCHIVE --> /labcas-data/ecas
    target_dir="$archive_dir/$dataset"
    if [ ! -d "$target_dir" ]; then
        echo "Symlinking $source_dir --> $target_dir"
 	ln -s $source_dir $target_dir
    fi

    # 2) unpublish old collection first
    # DANGER: PREVIOUSLY THIS WIPED OUT THE SOLR INDEX
    #echo "Unpublishing collection: $dataset"
    #$script1 $dataset
	
    # 3) publish collection=dataset
    echo "Publishing collection: $dataset"
    python $script2 --collection_dir=$archive_dir/$dataset --in_place=true
    
done




