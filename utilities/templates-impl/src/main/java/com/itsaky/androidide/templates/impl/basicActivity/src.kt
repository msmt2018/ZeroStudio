/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.templates.impl.basicActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.impl.base.baseLayoutContentMain
import com.itsaky.androidide.templates.impl.base.materialAppBar
import com.itsaky.androidide.templates.impl.base.materialFab
import com.itsaky.androidide.templates.impl.indentToLevel

internal fun AndroidModuleTemplateBuilder.basicActivitySrcKt() =
    """
package ${data.packageName}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ${data.packageName}.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = checkNotNull(_binding) { "Activity has been destroyed" }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.textView.text = "Hello, Basic Activity!"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
  """.trim()

internal fun AndroidModuleTemplateBuilder.basicActivitySrcJava() =
    """
package ${data.packageName};

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import ${data.packageName}.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.textView.setText("Hello, Basic Activity!");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
""".trim()

internal fun basicActivityLayout() =
    """
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">
    
    <TextView
        android:id="@+id/textView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        android:textSize="24sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />
        
</androidx.constraintlayout.widget.ConstraintLayout>
""".trim()

internal fun basicActivityContent() = ""