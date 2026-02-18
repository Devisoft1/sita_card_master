#!/usr/bin/env python3
"""
Script to remove black backgrounds from logo images and make them transparent.
This script processes PNG images and replaces black/near-black pixels with transparency.
"""

from PIL import Image
import os
import sys

def remove_black_background(input_path, output_path=None, threshold=50):
    """
    Remove black background from an image and make it transparent.
    
    Args:
        input_path: Path to the input image
        output_path: Path to save the output image (if None, overwrites input)
        threshold: RGB threshold for considering a pixel as "black" (0-255)
                  Lower values = only pure black, higher values = near-black colors too
    """
    try:
        # Open the image
        img = Image.open(input_path)
        
        # Convert to RGBA if not already
        img = img.convert("RGBA")
        
        # Get pixel data
        datas = img.getdata()
        
        new_data = []
        for item in datas:
            # Check if pixel is black or near-black
            # item is (R, G, B, A)
            if item[0] <= threshold and item[1] <= threshold and item[2] <= threshold:
                # Make it transparent
                new_data.append((255, 255, 255, 0))
            else:
                # Keep the original pixel
                new_data.append(item)
        
        # Update image data
        img.putdata(new_data)
        
        # Save the image
        if output_path is None:
            output_path = input_path
        
        img.save(output_path, "PNG")
        print(f"✓ Processed: {input_path}")
        return True
        
    except Exception as e:
        print(f"✗ Error processing {input_path}: {str(e)}")
        return False

def process_logo_directories():
    """Process all logo files in the project."""
    
    base_dir = os.path.dirname(os.path.abspath(__file__))
    
    # Define logo directories to process
    logo_dirs = [
        "composeApp/src/androidMain/res/drawable",
        "composeApp/src/commonMain/composeResources/drawable"
    ]
    
    # Logo files to process
    logo_files = ["logo.png", "logo_backup.png", "logo_original.png", "sita_logo.png"]
    
    processed_count = 0
    
    for logo_dir in logo_dirs:
        dir_path = os.path.join(base_dir, logo_dir)
        
        if not os.path.exists(dir_path):
            print(f"⚠ Directory not found: {dir_path}")
            continue
        
        print(f"\n📁 Processing directory: {logo_dir}")
        
        for logo_file in logo_files:
            file_path = os.path.join(dir_path, logo_file)
            
            if os.path.exists(file_path):
                # Create backup before processing
                backup_path = file_path.replace(".png", "_with_black.png")
                if not os.path.exists(backup_path):
                    img = Image.open(file_path)
                    img.save(backup_path)
                    print(f"  💾 Backup created: {logo_file}_with_black.png")
                
                # Process the image (threshold=50 means remove pure black and very dark colors)
                if remove_black_background(file_path, threshold=50):
                    processed_count += 1
    
    print(f"\n✅ Processed {processed_count} logo files")
    print("\nNote: Original files with black backgrounds are saved as *_with_black.png")

if __name__ == "__main__":
    print("=" * 60)
    print("Black Background Removal Tool for Logos")
    print("=" * 60)
    
    if len(sys.argv) > 1:
        # Process specific file
        input_file = sys.argv[1]
        threshold = int(sys.argv[2]) if len(sys.argv) > 2 else 50
        
        print(f"\nProcessing single file: {input_file}")
        print(f"Black threshold: {threshold}")
        
        remove_black_background(input_file, threshold=threshold)
    else:
        # Process all logo directories
        process_logo_directories()
    
    print("\n" + "=" * 60)
