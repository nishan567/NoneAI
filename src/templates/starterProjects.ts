import { ProjectTemplate } from '../types';

export const STARTER_TEMPLATES: ProjectTemplate[] = [
  {
    id: 'android-compose',
    name: 'Android Jetpack Compose (Kotlin)',
    category: 'Android',
    description: 'Modern Android architecture with Jetpack Compose, Material3, ViewModel, StateFlow, and Gradle Kotlin DSL (.kts)',
    icon: 'Smartphone',
    defaultRunCommand: './gradlew assembleDebug',
    buildInstructions: `### Android Project Build & Run Instructions
1. **Prerequisites**: JDK 17+ and Android SDK Build Tools (API 34/35).
2. **Build APK**: Run \`./gradlew assembleDebug\` in the root directory.
3. **Run Unit Tests**: Run \`./gradlew testDebugUnitTest\`.
4. **Deploy to Device**: Ensure ADB is connected and run \`./gradlew installDebug\`.
5. **APK Output**: Find the compiled debug binary at \`app/build/outputs/apk/debug/app-debug.apk\`.`,
    files: [
      {
        path: 'settings.gradle.kts',
        language: 'gradle',
        content: `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "NexusAndroidApp"
include(":app")
`,
      },
      {
        path: 'build.gradle.kts',
        language: 'gradle',
        content: `plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
`,
      },
      {
        path: 'app/build.gradle.kts',
        language: 'gradle',
        content: `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nexus.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nexus.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
`,
      },
      {
        path: 'app/src/main/AndroidManifest.xml',
        language: 'xml',
        content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@style/Theme.NexusApp">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.NexusApp">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
`,
      },
      {
        path: 'app/src/main/res/values/strings.xml',
        language: 'xml',
        content: `<resources>
    <string name="app_name">Nexus Android Studio</string>
    <string name="welcome_message">Welcome to your Jetpack Compose Workspace</string>
</resources>
`,
      },
      {
        path: 'app/src/main/res/values/themes.xml',
        language: 'xml',
        content: `<resources>
    <style name="Theme.NexusApp" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
`,
      },
      {
        path: 'app/src/main/java/com/nexus/app/MainActivity.kt',
        language: 'kotlin',
        content: `package com.nexus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nexus.app.ui.HomeScreen
import com.nexus.app.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}
`,
      },
      {
        path: 'app/src/main/java/com/nexus/app/ui/HomeScreen.kt',
        language: 'kotlin',
        content: `package com.nexus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    var textInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nexus Task Engine") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.addTask(textInput.trim())
                        textInput = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("New Action Item") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tasks) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge)
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { viewModel.toggleTask(task.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
`,
      },
      {
        path: 'app/src/main/java/com/nexus/app/ui/MainViewModel.kt',
        language: 'kotlin',
        content: `package com.nexus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.app.data.Task
import com.nexus.app.data.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: TaskRepository = TaskRepository()
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.tasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTask(title: String) {
        viewModelScope.launch {
            repository.addTask(title)
        }
    }

    fun toggleTask(id: String) {
        viewModelScope.launch {
            repository.toggleTask(id)
        }
    }
}
`,
      },
      {
        path: 'app/src/main/java/com/nexus/app/data/TaskRepository.kt',
        language: 'kotlin',
        content: `package com.nexus.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false
)

class TaskRepository {
    private val _tasks = MutableStateFlow<List<Task>>(
        listOf(
            Task(title = "Initialize Jetpack Compose project", isCompleted = true),
            Task(title = "Configure Gradle KTS build dependencies", isCompleted = true),
            Task(title = "Connect Room SQLite persistent storage", isCompleted = false),
            Task(title = "Integrate Gemini AI assistant actions", isCompleted = false)
        )
    )
    val tasks: Flow<List<Task>> = _tasks.asStateFlow()

    fun addTask(title: String) {
        _tasks.value = _tasks.value + Task(title = title)
    }

    fun toggleTask(id: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
        }
    }
}
`,
      },
      {
        path: 'README.md',
        language: 'markdown',
        content: `# Nexus Android Jetpack Compose Project

A clean, production-grade Android starter project built with modern Android development standards.

## Architecture
- **Language**: Kotlin 1.9.22
- **UI Toolkit**: Jetpack Compose with Material3
- **Build System**: Gradle 8.2.2 with Kotlin DSL (\`build.gradle.kts\`)
- **State Management**: Kotlin Coroutines & StateFlow with MVVM
- **Target SDK**: Android 14 (API 34)

## How to Build & Run
Run the Android Builder in NexusCode or execute via sandboxed terminal:
\`\`\`bash
./gradlew assembleDebug
\`\`\`
`,
      },
    ],
  },
  {
    id: 'python-fastapi',
    name: 'Python FastAPI Microservice',
    category: 'Python',
    description: 'High-performance async REST API with Pydantic validation, CORS middleware, and PyTest test suite',
    icon: 'Terminal',
    defaultRunCommand: 'python -m uvicorn main:app --reload',
    buildInstructions: `### Python API Run Instructions
1. Install requirements: \`pip install -r requirements.txt\`
2. Run server: \`uvicorn main:app --reload --port 8000\`
3. Run tests: \`pytest\``,
    files: [
      {
        path: 'main.py',
        language: 'python',
        content: `from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional
import uuid

app = FastAPI(
    title="Nexus Microservice API",
    version="1.0.0",
    description="High-performance async API with Pydantic validation"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ProjectItem(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    title: str
    language: str
    status: str = "active"
    stars: int = 0

database: List[ProjectItem] = [
    ProjectItem(title="Android Compose App", language="kotlin", stars=42),
    ProjectItem(title="FastAPI Service", language="python", stars=89),
    ProjectItem(title="React IDE Studio", language="typescript", stars=120),
]

@app.get("/")
async def root():
    return {"message": "Nexus Microservice is healthy", "status": "UP"}

@app.get("/api/projects", response_model=List[ProjectItem])
async def get_projects(language: Optional[str] = None):
    if language:
        return [p for p in database if p.language.lower() == language.lower()]
    return database

@app.post("/api/projects", response_model=ProjectItem, status_code=status.HTTP_201_CREATED)
async def create_project(item: ProjectItem):
    database.append(item)
    return item

@app.get("/api/projects/{item_id}", response_model=ProjectItem)
async def get_project(item_id: str):
    for p in database:
        if p.id == item_id:
            return p
    raise HTTPException(status_code=404, detail="Project not found")
`,
      },
      {
        path: 'requirements.txt',
        language: 'plaintext',
        content: `fastapi>=0.110.0
uvicorn[standard]>=0.28.0
pydantic>=2.6.4
pytest>=8.0.0
httpx>=0.27.0
`,
      },
      {
        path: 'test_main.py',
        language: 'python',
        content: `from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_root():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"

def test_get_projects():
    response = client.get("/api/projects")
    assert response.status_code == 200
    assert len(response.json()) >= 3

def test_create_project():
    new_proj = {"title": "Test AI Pipeline", "language": "python", "stars": 5}
    response = client.post("/api/projects", json=new_proj)
    assert response.status_code == 201
    data = response.json()
    assert data["title"] == "Test AI Pipeline"
    assert "id" in data
`,
      },
    ],
  },
  {
    id: 'java-spring',
    name: 'Java Spring Boot REST Service',
    category: 'Java',
    description: 'Enterprise Spring Boot 3 with Spring Web, Maven pom.xml, JPA Model, and JUnit 5 tests',
    icon: 'Coffee',
    defaultRunCommand: './mvnw spring-boot:run',
    buildInstructions: `### Java Spring Boot Instructions
1. Build with Maven: \`./mvnw clean package\`
2. Run test suite: \`./mvnw test\`
3. Run application: \`./mvnw spring-boot:run\``,
    files: [
      {
        path: 'pom.xml',
        language: 'xml',
        content: `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.3</version>
        <relativePath/>
    </parent>
    <groupId>com.nexus</groupId>
    <artifactId>nexus-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>nexus-service</name>
    <description>Nexus Java Spring Boot Service</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
`,
      },
      {
        path: 'src/main/java/com/nexus/service/Application.java',
        language: 'java',
        content: `package com.nexus.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.java, args);
    }
}
`,
      },
      {
        path: 'src/main/java/com/nexus/service/controller/ApiController.java',
        language: 'java',
        content: `package com.nexus.service.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "HEALTHY");
        response.put("engine", "Spring Boot 3.2.3");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
`,
      },
    ],
  },
  {
    id: 'typescript-web',
    name: 'TypeScript & React Application',
    category: 'JavaScript/TypeScript',
    description: 'Modern SPA with Vite, React 19, TypeScript, and Tailwind CSS',
    icon: 'Layers',
    defaultRunCommand: 'npm run dev',
    buildInstructions: `### TypeScript Web App Instructions
1. Install dependencies: \`npm install\`
2. Start dev server: \`npm run dev\`
3. Production build: \`npm run build\``,
    files: [
      {
        path: 'src/App.tsx',
        language: 'typescript',
        content: `import React, { useState } from 'react';

export default function App() {
  const [count, setCount] = useState(0);

  return (
    <div className="min-h-screen bg-zinc-950 text-white flex flex-col items-center justify-center p-6">
      <div className="max-w-md w-full bg-zinc-900 border border-zinc-800 rounded-xl p-8 shadow-2xl text-center">
        <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-400 to-indigo-500 bg-clip-text text-transparent mb-4">
          Nexus TypeScript App
        </h1>
        <p className="text-zinc-400 mb-6">
          Interactive Vite + React 19 workspace with Tailwind CSS.
        </p>
        <button
          onClick={() => setCount((c) => c + 1)}
          className="px-6 py-2.5 bg-blue-600 hover:bg-blue-500 transition-colors font-medium rounded-lg shadow-lg shadow-blue-500/20 active:scale-95"
        >
          Clicked {count} times
        </button>
      </div>
    </div>
  );
}
`,
      },
      {
        path: 'package.json',
        language: 'json',
        content: `{
  "name": "nexus-web-app",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  },
  "devDependencies": {
    "typescript": "^5.4.0",
    "vite": "^5.1.0"
  }
}
`,
      },
    ],
  },
  {
    id: 'html-css-web',
    name: 'Modern HTML5 / CSS3 / JavaScript',
    category: 'Web',
    description: 'Clean responsive web app with semantic HTML5, modern flexbox/grid CSS, and interactive vanilla JavaScript',
    icon: 'Globe',
    defaultRunCommand: 'npx serve .',
    buildInstructions: `### Web App Run Instructions
Open \`index.html\` directly in any modern browser or run with a local web server:
\`\`\`bash
npx serve .
\`\`\``,
    files: [
      {
        path: 'index.html',
        language: 'html',
        content: `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Nexus Canvas</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <div class="container">
    <header>
      <h1>Interactive Canvas Workspace</h1>
      <p>Created with HTML5, CSS3, and JavaScript</p>
    </header>
    <main>
      <div class="controls">
        <button id="addCardBtn">Add Workspace Item</button>
        <button id="clearBtn">Clear Canvas</button>
      </div>
      <div id="board" class="board"></div>
    </main>
  </div>
  <script src="app.js"></script>
</body>
</html>
`,
      },
      {
        path: 'style.css',
        language: 'css',
        content: `* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  background-color: #0f172a;
  color: #f8fafc;
  min-height: 100vh;
  padding: 2rem;
}
.container {
  max-width: 900px;
  margin: 0 auto;
}
header h1 {
  font-size: 2.25rem;
  font-weight: 700;
  color: #38bdf8;
  margin-bottom: 0.5rem;
}
.controls {
  margin: 1.5rem 0;
  display: flex;
  gap: 1rem;
}
button {
  background: #2563eb;
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  font-size: 1rem;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: background 0.2s;
}
button:hover {
  background: #1d4ed8;
}
.board {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 1rem;
}
.card {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 0.5rem;
  padding: 1.25rem;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}
`,
      },
      {
        path: 'app.js',
        language: 'javascript',
        content: `document.addEventListener('DOMContentLoaded', () => {
  const addBtn = document.getElementById('addCardBtn');
  const clearBtn = document.getElementById('clearBtn');
  const board = document.getElementById('board');
  let counter = 1;

  addBtn.addEventListener('click', () => {
    const card = document.createElement('div');
    card.className = 'card';
    card.innerHTML = \`
      <h3>Module #\${counter}</h3>
      <p>Initialized at \${new Date().toLocaleTimeString()}</p>
    \`;
    board.appendChild(card);
    counter++;
  });

  clearBtn.addEventListener('click', () => {
    board.innerHTML = '';
    counter = 1;
  });
});
`,
      },
    ],
  },
];
