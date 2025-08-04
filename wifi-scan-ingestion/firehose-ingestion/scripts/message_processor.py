#!/usr/bin/env python3

"""
WiFi Scan Message Processor for Firehose Testing
================================================

This script processes WiFi scan JSON messages by:
1. Compressing the JSON data using gzip
2. Encoding the compressed data as base64
3. Providing utilities for testing Kinesis Firehose ingestion

Usage:
    python3 message_processor.py --file sample_data.json
    python3 message_processor.py --compress --json '{"data": "test"}'
    python3 message_processor.py --decompress --base64 <encoded_string>
"""

import json
import gzip
import base64
import argparse
import sys
import os
from datetime import datetime
from typing import Dict, Any, Optional, Union


class WifiScanMessageProcessor:
    """Handles compression and encoding of WiFi scan messages."""
    
    def __init__(self):
        self.compression_level = 6  # Default gzip compression level (1-9)
    
    def compress_message(self, message: Union[str, Dict[Any, Any]]) -> bytes:
        """
        Compress a WiFi scan message using gzip.
        
        Args:
            message: JSON string or dictionary to compress
            
        Returns:
            Compressed bytes
        """
        if isinstance(message, dict):
            message_str = json.dumps(message, separators=(',', ':'))
        else:
            message_str = str(message)
        
        return gzip.compress(message_str.encode('utf-8'), compresslevel=self.compression_level)
    
    def decompress_message(self, compressed_data: bytes) -> str:
        """
        Decompress gzipped data back to JSON string.
        
        Args:
            compressed_data: Gzipped bytes
            
        Returns:
            Original JSON string
        """
        return gzip.decompress(compressed_data).decode('utf-8')
    
    def encode_base64(self, data: bytes) -> str:
        """
        Encode bytes data to base64 string.
        
        Args:
            data: Bytes to encode
            
        Returns:
            Base64 encoded string
        """
        return base64.b64encode(data).decode('utf-8')
    
    def decode_base64(self, encoded_data: str) -> bytes:
        """
        Decode base64 string to bytes.
        
        Args:
            encoded_data: Base64 encoded string
            
        Returns:
            Decoded bytes
        """
        return base64.b64decode(encoded_data.encode('utf-8'))
    
    def process_message(self, message: Union[str, Dict[Any, Any]]) -> Dict[str, Any]:
        """
        Complete processing pipeline: compress + base64 encode a message.
        
        Args:
            message: WiFi scan message to process
            
        Returns:
            Dictionary with processing results and metadata
        """
        # Convert to string if dict
        if isinstance(message, dict):
            original_json = json.dumps(message, separators=(',', ':'))
        else:
            original_json = str(message)
        
        # Get original size
        original_size = len(original_json.encode('utf-8'))
        
        # Compress
        compressed_data = self.compress_message(message)
        compressed_size = len(compressed_data)
        
        # Encode to base64
        encoded_data = self.encode_base64(compressed_data)
        encoded_size = len(encoded_data.encode('utf-8'))
        
        # Calculate compression ratio
        compression_ratio = (1 - compressed_size / original_size) * 100 if original_size > 0 else 0
        
        return {
            'original_size': original_size,
            'compressed_size': compressed_size,
            'encoded_size': encoded_size,
            'compression_ratio': round(compression_ratio, 2),
            'compressed_data': compressed_data,
            'encoded_data': encoded_data,
            'processing_timestamp': datetime.utcnow().isoformat() + 'Z'
        }
    
    def load_json_file(self, file_path: str) -> Dict[Any, Any]:
        """
        Load JSON data from file.
        
        Args:
            file_path: Path to JSON file
            
        Returns:
            Parsed JSON data
        """
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except FileNotFoundError:
            raise FileNotFoundError(f"File not found: {file_path}")
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid JSON in file {file_path}: {e}")
    
    def save_processed_data(self, processed_data: Dict[str, Any], output_file: str) -> None:
        """
        Save processed data to file.
        
        Args:
            processed_data: Result from process_message()
            output_file: Output file path
        """
        output = {
            'metadata': {
                'original_size': processed_data['original_size'],
                'compressed_size': processed_data['compressed_size'],
                'encoded_size': processed_data['encoded_size'],
                'compression_ratio': processed_data['compression_ratio'],
                'processing_timestamp': processed_data['processing_timestamp']
            },
            'encoded_data': processed_data['encoded_data']
        }
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(output, f, indent=2)
    
    def create_firehose_payload(self, message: Union[str, Dict[Any, Any]], record_id: Optional[str] = None) -> Dict[str, Any]:
        """
        Create a Kinesis Firehose-compatible payload.
        
        Args:
            message: WiFi scan message
            record_id: Optional record ID
            
        Returns:
            Firehose-compatible payload
        """
        processed = self.process_message(message)
        
        return {
            'DeliveryStreamName': 'MVS-stream',
            'Record': {
                'Data': processed['encoded_data']
            }
        }


def main():
    """Command line interface for the message processor."""
    parser = argparse.ArgumentParser(description='WiFi Scan Message Processor')
    parser.add_argument('--file', '-f', help='Input JSON file path')
    parser.add_argument('--json', '-j', help='Input JSON string')
    parser.add_argument('--output', '-o', help='Output file path')
    parser.add_argument('--compress', action='store_true', help='Compress and encode message')
    parser.add_argument('--decompress', action='store_true', help='Decompress base64 encoded data')
    parser.add_argument('--base64', '-b', help='Base64 encoded data to decompress')
    parser.add_argument('--firehose', action='store_true', help='Create Firehose-compatible payload')
    parser.add_argument('--verbose', '-v', action='store_true', help='Verbose output')
    
    args = parser.parse_args()
    
    processor = WifiScanMessageProcessor()
    
    try:
        if args.decompress and args.base64:
            # Decompress base64 data
            decoded_bytes = processor.decode_base64(args.base64)
            decompressed_message = processor.decompress_message(decoded_bytes)
            
            if args.verbose:
                print("=== Decompression Results ===")
                print(f"Decoded size: {len(decoded_bytes)} bytes")
                print(f"Decompressed size: {len(decompressed_message)} bytes")
                print()
            
            print("Decompressed message:")
            try:
                # Pretty print if valid JSON
                parsed = json.loads(decompressed_message)
                print(json.dumps(parsed, indent=2))
            except json.JSONDecodeError:
                print(decompressed_message)
        
        elif args.compress:
            # Load message
            if args.file:
                message = processor.load_json_file(args.file)
            elif args.json:
                message = json.loads(args.json)
            else:
                print("Error: Specify --file or --json for compression", file=sys.stderr)
                sys.exit(1)
            
            # Process message
            if args.firehose:
                result = processor.create_firehose_payload(message)
                if args.verbose:
                    print("=== Firehose Payload Created ===")
                    print(f"DeliveryStreamName: {result['DeliveryStreamName']}")
                    print(f"Data size: {len(result['Record']['Data'])} characters")
                    print()
                
                if args.output:
                    with open(args.output, 'w', encoding='utf-8') as f:
                        json.dump(result, f, indent=2)
                else:
                    print(json.dumps(result, indent=2))
            else:
                processed = processor.process_message(message)
                
                if args.verbose:
                    print("=== Processing Results ===")
                    print(f"Original size: {processed['original_size']} bytes")
                    print(f"Compressed size: {processed['compressed_size']} bytes")
                    print(f"Encoded size: {processed['encoded_size']} bytes")
                    print(f"Compression ratio: {processed['compression_ratio']}%")
                    print(f"Processing timestamp: {processed['processing_timestamp']}")
                    print()
                
                if args.output:
                    processor.save_processed_data(processed, args.output)
                    print(f"Results saved to: {args.output}")
                else:
                    print("Base64 encoded data:")
                    print(processed['encoded_data'])
        
        else:
            parser.print_help()
            sys.exit(1)
    
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main() 