import sys
import os
import argparse
import logging
from gov.nasa.jpl.edrn.labcas.client.metadata_utils import read_config_metadata
from gov.nasa.jpl.edrn.labcas.client.solr_client import SolrClient
from gov.nasa.jpl.edrn.labcas.client.labcas_dataset_publisher import LabcasDatasetPublisher
from gov.nasa.jpl.edrn.labcas.utils import str2bool

logging.basicConfig(level=logging.DEBUG)


class LabcasCollectionPublisher(object):
    '''
    Publishes all datasets within a collection
    '''
    
    def __init__(self, 
                 solr_url='https://localhost:8984/solr', 
                 workflow_url='http://localhost:9001',
                 update_collection=True):
        
        self._solr_url = solr_url
        self._workflow_url = workflow_url
        self._solr_client = SolrClient(solr_url)
        self._update_collection = update_collection
        
    def crawl(self, directory_path, in_place=True, 
              update_datasets=True, update_files=True):
        
        # assemble collection metadata
        metadata = self._get_collection_metadata(directory_path)
                
        # loop over all sub-directories to publish datasets
        labcasDatasetPublisher = LabcasDatasetPublisher(metadata['CollectionName'], 
                                                        solr_url=self._solr_url,
                                                        workflow_url=self._workflow_url,
                                                        update_collection=self._update_collection)
        
        if update_datasets:
            for subdir_name in os.listdir(directory_path):
                subdir_path = os.path.join(directory_path, subdir_name)
                if os.path.isdir(subdir_path):
                    
                    # publish dataset hierarchy
                    labcasDatasetPublisher.crawl(subdir_path, 
                                                 in_place=in_place, 
                                                 update_datasets=update_datasets,
                                                 update_files=update_files)
                
        # update collection metadata in Solr
        # AFTER minimal metadata has been entered while publishing datasets
        if self._update_collection:
            metadata['id'] = metadata['CollectionId']
            del metadata['CollectionId']
            self._solr_client.post(metadata, "collections")

    
    def _get_collection_metadata(self, directory_path):
        '''
        Collects collection for a given directory path
        '''
        
        # remove last '/' otherwise the path is not split correctly
        if directory_path.endswith('/'):
            directory_path = directory_path[:-1]
        (parent_path, this_dir_name) = os.path.split(directory_path)
        
        # read metadata from configuration files, if found
        metadata = read_config_metadata(directory_path)
        
        # use default metadata values if not populated from configuration
        if not metadata.get("CollectionName", None):
            metadata["CollectionName"] = this_dir_name
        if not metadata.get("CollectionId", None):
            metadata["CollectionId"] = metadata["CollectionName"].replace(" ","_")

        return metadata


    
if __name__ == '__main__':
    
    # parse command line arguments
    parser = argparse.ArgumentParser()
    parser.add_argument('--collection_dir', type=str, help='Collection root directory')
    parser.add_argument('--in_place', type=str2bool, default=True,
                        help='Option flag to publish data without moving it (default: True)')
    parser.add_argument('--update_collection', type=str2bool, default=True,
                        help='Optional flag to update the collection metadata (default: True)')
    parser.add_argument('--update_datasets', type=str2bool, default=True,
                        help='Optional flag to update the datasets metadata (default: True)')
    parser.add_argument('--update_files', type=str2bool, default=True,
                        help='Optional flag to publish files (default: True)')
    parser.add_argument('--solr_url', type=str, default='https://localhost:8984/solr',
                        help='URL of Solr Index')
    parser.add_argument('--workflow_url', type=str, default='http://localhost:9001',
                        help='URL of Workflow Manager XML/RPC server')

    args_dict = vars( parser.parse_args() )
        
    # start publishing
    labcasCollectionPublisher = LabcasCollectionPublisher(
        solr_url=args_dict['solr_url'],
        workflow_url=args_dict['workflow_url'],
        update_collection=args_dict['update_collection']
        )
    labcasCollectionPublisher.crawl(args_dict['collection_dir'], 
                                    in_place=args_dict['in_place'],
                                    update_datasets=args_dict['update_datasets'],
                                    update_files=args_dict['update_files'])
