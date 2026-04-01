package com.itsaky.androidide.fragments.git.function

// import com.itsaky.androidide.resources.R
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catpuppyapp.puppygit.compose.ConfirmDialog
import com.catpuppyapp.puppygit.compose.ConfirmDialog2
import com.catpuppyapp.puppygit.compose.CopyScrollableColumn
import com.catpuppyapp.puppygit.compose.DefaultPaddingRow
import com.catpuppyapp.puppygit.compose.DepthTextField
import com.catpuppyapp.puppygit.compose.InternalFileChooser
import com.catpuppyapp.puppygit.compose.MyHorizontalDivider
import com.catpuppyapp.puppygit.compose.MySelectionContainer
import com.catpuppyapp.puppygit.compose.PasswordTextFiled
import com.catpuppyapp.puppygit.compose.SingleSelectList
import com.catpuppyapp.puppygit.compose.SingleSelection
import com.catpuppyapp.puppygit.compose.TokenInsteadOfPasswordHint
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.constants.SpecialCredential
import com.catpuppyapp.puppygit.data.entity.CredentialEntity
import com.catpuppyapp.puppygit.data.entity.RepoEntity
import com.catpuppyapp.puppygit.dev.dev_EnableUnTestedFeature
import com.catpuppyapp.puppygit.dev.shallowAndSingleBranchTestPassed
import com.catpuppyapp.puppygit.dto.NameAndPath
import com.catpuppyapp.puppygit.dto.NameAndPathType
import com.catpuppyapp.puppygit.play.pro.R
import com.catpuppyapp.puppygit.screen.shared.SharedState
import com.catpuppyapp.puppygit.style.MyStyleKt
import com.catpuppyapp.puppygit.ui.theme.Theme
import com.catpuppyapp.puppygit.user.UserUtil
import com.catpuppyapp.puppygit.utils.AppModel
import com.catpuppyapp.puppygit.utils.FsUtils
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.catpuppyapp.puppygit.utils.Msg
import com.catpuppyapp.puppygit.utils.MyLog
import com.catpuppyapp.puppygit.utils.boolToDbInt
import com.catpuppyapp.puppygit.utils.cache.Cache
import com.catpuppyapp.puppygit.utils.dbIntToBool
import com.catpuppyapp.puppygit.utils.doJobThenOffLoading
import com.catpuppyapp.puppygit.utils.filterAndMap
import com.catpuppyapp.puppygit.utils.getRepoNameFromGitUrl
import com.catpuppyapp.puppygit.utils.isPathExists
import com.catpuppyapp.puppygit.utils.state.mutableCustomStateListOf
import com.catpuppyapp.puppygit.utils.state.mutableCustomStateOf
import com.catpuppyapp.puppygit.utils.storagepaths.StoragePathsMan
import com.github.git24j.core.Clone
import com.github.git24j.core.Remote
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.ui.themes.*
import com.itsaky.androidide.utils.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ZeroCloneDialogBottomSheetFragment"

/** @author android_zero */
class ZeroCloneDialogBottomSheetFragment : BottomSheetDialogFragment() {

  companion object {
    private const val ARG_REPO_ID = "arg_repo_id"

    fun newInstance(repoId: String = ""): ZeroCloneDialogBottomSheetFragment {
      val fragment = ZeroCloneDialogBottomSheetFragment()
      val args = Bundle()
      args.putString(ARG_REPO_ID, repoId)
      fragment.arguments = args
      return fragment
    }
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {

    // 获取参数
    val repoId = arguments?.getString(ARG_REPO_ID) ?: ""

    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        // set theme
        InternalTheme { CloneScreenContent(repoId = repoId, onDismiss = { dismiss() }) }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    dialog?.setOnShowListener { dialog ->
      val d = dialog as BottomSheetDialog
      val bottomSheet =
          d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
      bottomSheet?.let {
        val behavior = BottomSheetBehavior.from(it)

        // 获取屏幕高度
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        // 设置 Peek Height 为屏幕的 75%
        behavior.peekHeight = (screenHeight * 0.75).toInt()

        // 初始状态设为折叠 (即显示 Peek Height 高度)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // 允许向上拖拽至全屏
        behavior.skipCollapsed = false
        behavior.isDraggable = true
      }
    }
  }

  @Composable
  private fun InternalTheme(
      darkTheme: Boolean = isSystemInDarkTheme(),
      content: @Composable () -> Unit,
  ) {
    val colorScheme =
        if (darkTheme) {
          darkColorScheme()
        } else {
          lightColorScheme()
        }

    MaterialTheme(colorScheme = colorScheme, content = content)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloneScreenContent(
    repoId: String,
    onDismiss: () -> Unit,
) {
  val stateKeyTag = Cache.getSubPageKey(TAG)

  val activityContext = LocalContext.current
  val inDarkTheme = Theme.inDarkTheme
  val scope = rememberCoroutineScope()

  val isEditMode = repoId.isNotBlank() && repoId != Cons.dbInvalidNonEmptyId
  val repoFromDb =
      mutableCustomStateOf(
          keyTag = stateKeyTag,
          keyName = "repoFromDb",
          initValue = RepoEntity(id = ""),
      )

  // 这个变量本来是用于获取应用默认仓库父目录的，现在我们将直接使用 Environment.PROJECTS_DIR
  // val allRepoParentDir = AppModel.allRepoParentDir

  val gitUrl = rememberSaveable { mutableStateOf("") }
  val repoName =
      mutableCustomStateOf(
          keyTag = stateKeyTag,
          keyName = "repoName",
          initValue = TextFieldValue(""),
      )
  val branch = rememberSaveable { mutableStateOf("") }
  val depth = rememberSaveable { mutableStateOf("") }
  val credentialName =
      mutableCustomStateOf(
          keyTag = stateKeyTag,
          keyName = "credentialName",
          initValue = TextFieldValue(""),
      )
  val credentialVal = rememberSaveable { mutableStateOf("") }
  val credentialPass = rememberSaveable { mutableStateOf("") }

  val gitUrlType = rememberSaveable { mutableIntStateOf(Cons.gitUrlTypeHttp) }

  val curCredentialType = rememberSaveable { mutableIntStateOf(Cons.dbCredentialTypeHttp) }
  val allCredentialList =
      mutableCustomStateListOf(
          keyTag = stateKeyTag,
          keyName = "allCredentialList",
          initValue = listOf<CredentialEntity>(),
      )
  val selectedCredential =
      mutableCustomStateOf(
          keyTag = stateKeyTag,
          keyName = "selectedCredential",
          initValue = CredentialEntity(id = ""),
      )

  // 获取输入焦点
  val focusRequesterGitUrl = remember { FocusRequester() }
  val focusRequesterRepoName = remember { FocusRequester() }
  val focusRequesterCredentialName = remember { FocusRequester() }
  val focusToNone = 0
  val focusToGitUrl = 1
  val focusToRepoName = 2
  val focusToCredentialName = 3
  val requireFocusTo = rememberSaveable { mutableIntStateOf(focusToNone) }

  val noCredential = stringResource(R.string.no_credential)
  val newCredential = stringResource(R.string.new_credential)
  val selectCredential = stringResource(R.string.select_credential)
  val matchCredentialByDomain = stringResource(R.string.match_credential_by_domain)

  val optNumNoCredential = 0
  val optNumNewCredential = 1
  val optNumSelectCredential = 2
  val optNumMatchCredentialByDomain = 3
  val credentialRadioOptions =
      listOf(noCredential, newCredential, selectCredential, matchCredentialByDomain)
  val (credentialSelectedOption, onCredentialOptionSelected) =
      rememberSaveable { mutableIntStateOf(optNumNoCredential) }

  val (isRecursiveClone, onIsRecursiveCloneStateChange) = rememberSaveable { mutableStateOf(false) }
  val (isSingleBranch, onIsSingleBranchStateChange) = rememberSaveable { mutableStateOf(false) }

  val isReadyForClone = rememberSaveable { mutableStateOf(false) }

  val passwordVisible = rememberSaveable { mutableStateOf(false) }

  val showRepoNameAlreadyExistsErr = rememberSaveable { mutableStateOf(false) }
  val showCredentialNameAlreadyExistsErr = rememberSaveable { mutableStateOf(false) }
  val showRepoNameHasIllegalCharsOrTooLongErr = rememberSaveable { mutableStateOf(false) }

  val updateRepoName: (TextFieldValue) -> Unit = {
    val newVal = it
    val oldVal = repoName.value

    if (oldVal.text != newVal.text) {
      showRepoNameAlreadyExistsErr.value = false
      showRepoNameHasIllegalCharsOrTooLongErr.value = false
    }
    repoName.value = newVal
  }
  val updateCredentialName: (TextFieldValue) -> Unit = {
    val newVal = it
    val oldVal = credentialName.value

    if (oldVal.text != newVal.text) {
      if (showCredentialNameAlreadyExistsErr.value) {
        showCredentialNameAlreadyExistsErr.value = false
      }
    }
    credentialName.value = newVal
  }
  val focusRepoName: () -> Unit = {
    val text = repoName.value.text
    repoName.value = repoName.value.copy(selection = TextRange(0, text.length))
    requireFocusTo.intValue = focusToRepoName
  }
  val setCredentialNameExistAndFocus: () -> Unit = {
    showCredentialNameAlreadyExistsErr.value = true
    val text = credentialName.value.text
    credentialName.value = credentialName.value.copy(selection = TextRange(0, text.length))
    requireFocusTo.intValue = focusToCredentialName
  }

  val getStoragePathList = {
    // 使用 Environment.PROJECTS_DIR 作为默认存储路径
    val defaultPath = Environment.PROJECTS_DIR.canonicalPath
    mutableListOf(
        NameAndPath(
            activityContext.getString(R.string.internal_storage),
            defaultPath,
            NameAndPathType.APP_ACCESSIBLE_STORAGES,
        )
    )
  }

  val storagePathList =
      mutableCustomStateListOf(
          keyTag = stateKeyTag,
          keyName = "storagePathList",
          initValue = getStoragePathList(),
      )

  val storagePathSelectedPath = rememberSaveable { mutableStateOf(storagePathList.value.first()) }

  val storagePathSelectedIndex = rememberSaveable { mutableIntStateOf(0) }

  val showAddStoragePathDialog = rememberSaveable { mutableStateOf(false) }
  val storagePathForAdd = rememberSaveable { SharedState.fileChooser_DirPath }

  val findStoragePathItemByPath = { path: String ->
    var ret = Pair<Int, NameAndPath?>(-1, null)
    for ((idx, item) in storagePathList.value.withIndex()) {
      if (item.path == path) {
        ret = Pair(idx, item)
        break
      }
    }
    ret
  }

  // --- Clone Progress & Logic States ---
  val isCloning = rememberSaveable { mutableStateOf(false) } // Controls "Loading..." text
  val showCloneProgressDialog = rememberSaveable { mutableStateOf(false) }
  // 0f = 0%, 1f = 100%, -1f = indeterminate
  val cloneProgress = remember { mutableFloatStateOf(-1f) }
  val cloneStatus = remember { mutableStateOf("") }
  val clonedRepoEntity = remember { mutableStateOf<RepoEntity?>(null) }
  val showOpenProjectDialog = rememberSaveable { mutableStateOf(false) }
  val isCloneError = remember { mutableStateOf(false) }

  fun openProject(context: Context, path: String) {
    val root = File(path)
    if (root.exists()) {
      // Call IProjectManager to open project
      IProjectManager.getInstance().openProject(root)
      // Launch EditorActivity
      val intent = Intent(context, EditorActivityKt::class.java)
      context.startActivity(intent)
    } else {
      Toast.makeText(context, R.string.msg_opened_project_does_not_exist, Toast.LENGTH_SHORT).show()
    }
  }

  if (showAddStoragePathDialog.value) {
    ConfirmDialog(
        title = stringResource(R.string.add_storage_path),
        requireShowTextCompose = true,
        textCompose = {
          MySelectionContainer {
            Column(
                modifier =
                    Modifier.fillMaxWidth().padding(5.dp).verticalScroll(rememberScrollState())
            ) {
              InternalFileChooser(activityContext, path = storagePathForAdd)
            }
          }
        },
        okBtnText = stringResource(R.string.ok),
        cancelBtnText = stringResource(R.string.cancel),
        okBtnEnabled = storagePathForAdd.value.isNotBlank(),
        onCancel = {
          showAddStoragePathDialog.value = false
          // Reset list from current state
          val currentList = storagePathList.value.toList()
          storagePathList.value.apply {
            clear()
            addAll(currentList)
          }
        },
    ) {
      showAddStoragePathDialog.value = false
      val storagePathForAddValue = storagePathForAdd.value

      doJobThenOffLoading {
        try {
          val newPathRet = FsUtils.userInputPathToCanonical(storagePathForAddValue)
          if (newPathRet.hasError()) {
            throw RuntimeException(activityContext.getString(R.string.invalid_path))
          }
          val newPath = newPathRet.data!!
          if (File(newPath).isDirectory.not()) {
            throw RuntimeException(activityContext.getString(R.string.path_is_not_a_dir))
          }

          // 这里需要重新获取完整的列表，确保是新的
          val currentList = storagePathList.value

          val spForSave = StoragePathsMan.get()
          val (indexOfStoragePath, existedStoragePath) = findStoragePathItemByPath(newPath)

          if (indexOfStoragePath != -1) {
            storagePathSelectedPath.value = existedStoragePath!!
            storagePathSelectedIndex.intValue = indexOfStoragePath
            spForSave.storagePathLastSelected = newPath
          } else {
            val newItem =
                NameAndPath.genByPath(newPath, NameAndPathType.REPOS_STORAGE_PATH, activityContext)
            currentList.add(newItem)
            val newItemIndex = currentList.size - 1
            storagePathSelectedIndex.intValue = newItemIndex
            storagePathSelectedPath.value = newItem
            spForSave.storagePaths.add(newPath)
            spForSave.storagePathLastSelected = newPath
          }
          StoragePathsMan.save(spForSave)
        } catch (e: Exception) {
          Msg.requireShowLongDuration("err: ${e.localizedMessage}")
          MyLog.e(TAG, "add storage path at `$TAG` err: ${e.stackTraceToString()}")
        }
      }
    }
  }

  val indexForDeleteStoragePathDialog = rememberSaveable { mutableStateOf(-1) }
  val showDeleteStoragePathListDialog = rememberSaveable { mutableStateOf(false) }
  val initDeleteStoragePathListDialog = { index: Int ->
    indexForDeleteStoragePathDialog.value = index
    showDeleteStoragePathListDialog.value = true
  }

  if (showDeleteStoragePathListDialog.value) {
    val targetPath =
        storagePathList.value.getOrNull(indexForDeleteStoragePathDialog.value)?.path ?: ""
    val closeDialog = { showDeleteStoragePathListDialog.value = false }

    val deleteStoragePath = j@{ index: Int ->
      if (storagePathList.value.getOrNull(index)?.type != NameAndPathType.REPOS_STORAGE_PATH) {
        Msg.requireShowLongDuration("can't remove item")
        return@j
      }

      storagePathList.value.removeAt(index)
      val spForSave = StoragePathsMan.get()
      val removedCurrent = index == storagePathSelectedIndex.intValue
      if (removedCurrent) {
        val newCurrentIndex = 0
        storagePathSelectedIndex.intValue = newCurrentIndex
        val newCurrent = storagePathList.value[newCurrentIndex]
        storagePathSelectedPath.value = newCurrent
        spForSave.storagePathLastSelected = newCurrent.path
      }

      spForSave.storagePaths.clear()
      val list =
          storagePathList.value.filterAndMap({ it.type == NameAndPathType.REPOS_STORAGE_PATH }) {
            it.path
          }
      if (list.isNotEmpty()) {
        spForSave.storagePaths.addAll(list)
      }
      StoragePathsMan.save(spForSave)
    }

    ConfirmDialog2(
        title = stringResource(R.string.delete),
        requireShowTextCompose = true,
        textCompose = { CopyScrollableColumn { Text(targetPath) } },
        okBtnText = stringResource(R.string.delete),
        okTextColor = MyStyleKt.TextColor.danger(),
        onCancel = closeDialog,
    ) {
      closeDialog()
      val targetIndex = indexForDeleteStoragePathDialog.value
      doJobThenOffLoading { deleteStoragePath(targetIndex) }
    }
  }

  val showLoadingDialog = rememberSaveable { mutableStateOf(false) }

  // 宽松的文件名检查逻辑
  fun isValidFileName(name: String): Boolean {
    if (name.isBlank()) return false
    if (name.length > 255) return false // 常见文件系统限制
    // 正则：不允许 / \ : * ? " < > |
    val invalidChars = Regex("[\\\\/:*?\"<>|]")
    return !invalidChars.containsMatchIn(name)
  }

  // --- Clone Logic ---
  fun performClone(repoForSave: RepoEntity) {
    showCloneProgressDialog.value = true
    cloneProgress.floatValue = -1f // Indeterminate start
    cloneStatus.value = activityContext.getString(R.string.cloning)
    isCloneError.value = false

    // Start background clone job
    scope.launch(Dispatchers.IO) {
      try {
        withContext(Dispatchers.Main) {
          cloneStatus.value = activityContext.getString(R.string.cloning) + "..."
        }

        // 1. Prepare Options
        val options = Clone.Options.defaultOpts()
        if (Libgit2Helper.needSetDepth(repoForSave.depth)) {
          options.fetchOpts.depth = repoForSave.depth
        }

        // Handle Branch
        if (repoForSave.branch.isNotBlank()) {
          options.checkoutBranch = repoForSave.branch
          // Handle Single Branch (Remote Create Callback)
          if (dbIntToBool(repoForSave.isSingleBranch)) {
            options.setRemoteCreateCb { repo, name, url ->
              val refSpec = Libgit2Helper.getGitRemoteFetchRefSpec(name, repoForSave.branch)
              Remote.createWithFetchspec(repo, name, url, refSpec)
            }
          }
        }

        // Handle Credentials
        val credentialDb = AppModel.dbContainer.credentialRepository
        val credentialId = repoForSave.credentialIdForClone
        if (credentialId.isNotBlank()) {
          val cred =
              credentialDb.getByIdWithDecryptAndMatchByDomain(credentialId, repoForSave.cloneUrl)
          if (cred != null) {
            val type = Libgit2Helper.getCredentialTypeByUrl(repoForSave.cloneUrl)
            Libgit2Helper.setCredentialCbForRemoteCallbacks(options.fetchOpts.callbacks, type, cred)
          }
        }

        // Handle Cert Check (Important for SSH/Self-signed)
        Libgit2Helper.setCertCheckCallback(repoForSave.cloneUrl, options.fetchOpts.callbacks, null)

        // 2. Execute Clone
        // 使用 use 自动关闭仓库句柄
        Clone.cloneRepo(repoForSave.cloneUrl, repoForSave.fullSavePath, options).use { clonedRepo ->
          // 3. Post-Clone Logic (Update DB)

          // 设置成功状态
          repoForSave.workStatus = Cons.dbRepoWorkStatusUpToDate
          repoForSave.createErrMsg = ""
          repoForSave.lastUpdateTime = System.currentTimeMillis() / 1000

          // 刷新仓库信息 (从新克隆的仓库中读取 HEAD, 分支等)
          // Libgit2Helper.updateRepoInfo 通常用于从数据库加载后的更新，这里我们直接操作对象并利用 updateRepoInfo 的逻辑
          // 我们可以直接调用它来填充字段，因为 repoForSave 此时路径已存在
          Libgit2Helper.updateRepoInfo(
              repoForSave,
              requireQueryParentInfo = true,
              settings = com.catpuppyapp.puppygit.settings.SettingsUtil.getSettingsSnapshot(),
          )

          // Save to DB (Create Remote, Update Repo)
          val repoDb = AppModel.dbContainer.repoRepository
          repoDb.cloneDoneUpdateRepoAndCreateRemote(repoForSave)
        }

        // 4. Success UI
        withContext(Dispatchers.Main) {
          cloneProgress.floatValue = 1.0f
          cloneStatus.value = activityContext.getString(R.string.success)
          clonedRepoEntity.value = repoForSave

          // 稍微延迟一下让用户看到成功状态
          delay(500)
          showCloneProgressDialog.value = false
          showOpenProjectDialog.value = true
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          isCloneError.value = true
          cloneStatus.value = "Error: ${e.localizedMessage}"
          MyLog.e(TAG, "Clone failed: ${e.stackTraceToString()}")
        }

        // Cleanup on failure
        try {
          File(repoForSave.fullSavePath).deleteRecursively()
        } catch (ex: Exception) {}

        // Update DB as Error
        repoForSave.workStatus = Cons.dbRepoWorkStatusCloneErr
        repoForSave.createErrMsg = e.localizedMessage ?: "Unknown error"
        AppModel.dbContainer.repoRepository.update(repoForSave)
      } finally {
        withContext(Dispatchers.Main) {
          isCloning.value = false // Hide "Loading..." text
        }
      }
    }
  }

  val doSave: () -> Unit = {
    scope.launch(Dispatchers.IO) launch@{
      try {
      // Do NOT set showLoadingDialog = true here, we use the custom "Loading..." text and dialog
      // showLoadingDialog.value=true

      val repoNameText = repoName.value.text

      if (!isValidFileName(repoNameText)) {
        Msg.requireShowLongDuration(
            activityContext.getString(R.string.err_repo_name_has_illegal_chars_or_too_long)
        )
        focusRepoName()
        showRepoNameHasIllegalCharsOrTooLongErr.value = true
        // showLoadingDialog.value=false
        return@launch
      }

      val repoDb = AppModel.dbContainer.repoRepository
      val credentialDb = AppModel.dbContainer.credentialRepository
      val fullSavePath = File(storagePathSelectedPath.value.path, repoNameText).canonicalPath

      val isRepoNameExist =
          if (!isEditMode || repoNameText != repoFromDb.value.repoName) {
            repoDb.isRepoNameExist(repoNameText)
          } else {
            false
          }

      if (isRepoNameExist || isPathExists(null, fullSavePath)) {
        Msg.requireShowLongDuration(activityContext.getString(R.string.repo_name_exists_err))
        focusRepoName()
        showRepoNameAlreadyExistsErr.value = true
        // showLoadingDialog.value=false
        return@launch
      }

      var credentialIdForClone = ""

      var credentialForSave: CredentialEntity? = null
      if (credentialSelectedOption == optNumNewCredential) {
        val credentialNameText = credentialName.value.text
        val isCredentialNameExist = credentialDb.isCredentialNameExist(credentialNameText)
        if (isCredentialNameExist) {
          Msg.requireShowLongDuration(
              activityContext.getString(R.string.credential_name_exists_err)
          )
          setCredentialNameExistAndFocus()
          // showLoadingDialog.value=false
          return@launch
        }

        credentialForSave =
            CredentialEntity(
                name = credentialNameText,
                value = credentialVal.value,
                pass = credentialPass.value,
                type = curCredentialType.intValue,
            )
        credentialDb.insertWithEncrypt(credentialForSave)
        credentialIdForClone = credentialForSave.id
      } else if (credentialSelectedOption == optNumSelectCredential) {
        credentialIdForClone = selectedCredential.value.id
      } else if (credentialSelectedOption == optNumMatchCredentialByDomain) {
        credentialIdForClone = SpecialCredential.MatchByDomain.credentialId
      }

      var intDepth = 0
      var isShallow = Cons.dbCommonFalse
      if (depth.value.isNotBlank()) {
        try {
          intDepth = depth.value.toInt().coerceAtLeast(0)
        } catch (e: Exception) {
          intDepth = 0
          Log.d(
              TAG,
              "invalid depth value '${depth.value}', will use default value '0', err=${e.localizedMessage}",
          )
        }
        if (intDepth > 0) {
          isShallow = Cons.dbCommonTrue
        }
      }

      val repoForSave: RepoEntity =
          if (isEditMode) repoFromDb.value else RepoEntity(createBy = Cons.dbRepoCreateByClone)
      repoForSave.repoName = repoNameText
      repoForSave.fullSavePath = fullSavePath
      repoForSave.cloneUrl = gitUrl.value
      repoForSave.workStatus = Cons.dbRepoWorkStatusNotReadyNeedClone
      repoForSave.credentialIdForClone = credentialIdForClone
      repoForSave.isRecursiveCloneOn = boolToDbInt(isRecursiveClone)
      repoForSave.depth = intDepth
      repoForSave.isShallow = isShallow

      if (branch.value.isNotBlank()) {
        repoForSave.branch = branch.value
        repoForSave.isSingleBranch = boolToDbInt(isSingleBranch)
      } else {
        repoForSave.branch = ""
        repoForSave.isSingleBranch = Cons.dbCommonFalse
      }

      if (isEditMode) {
        repoDb.update(repoForSave)
      } else {
        repoDb.insert(repoForSave)
      }

      withContext(Dispatchers.Main) {
        // showLoadingDialog.value=false
        isCloning.value = true // Show "Loading..." text above TextField

        // Start Cloning Process (must be called on Main because it mutates Compose states)
        performClone(repoForSave)
      }

      // withMainContext {
      // onDismiss()
      // }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          isCloning.value = false
          showCloneProgressDialog.value = false
          isCloneError.value = true
          cloneStatus.value = "Error: ${e.localizedMessage ?: "Unknown error"}"
          Msg.requireShowLongDuration(cloneStatus.value)
        }
        MyLog.e(TAG, "Failed before clone started: ${e.stackTraceToString()}")
      }
    }
  }

  val loadingText = rememberSaveable { mutableStateOf(activityContext.getString(R.string.loading)) }
  val listState = rememberScrollState()
  val spacerPadding = 2.dp

  // --- Dialogs ---

  // 1. Clone Progress Dialog
  if (showCloneProgressDialog.value) {
    Dialog(
        onDismissRequest = { /* Prevent dismiss unless Cancel clicked or Error */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
      Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(text = stringResource(R.string.cloning), style = MaterialTheme.typography.titleLarge)
          Spacer(modifier = Modifier.height(16.dp))

          // Repo Info
          Text(
              text = "${stringResource(R.string.repo_name)}: ${repoName.value.text}",
              style = MaterialTheme.typography.bodyMedium,
          )
          Text(
              text = "${stringResource(R.string.url)}: ${gitUrl.value}",
              style = MaterialTheme.typography.bodySmall,
              color = Color.Gray,
          )

          Spacer(modifier = Modifier.height(24.dp))

          // Progress Indicator (Indeterminate or Determinate if libgit2 supports it)
          if (cloneProgress.floatValue >= 0f) {
            LinearProgressIndicator(
                progress = { cloneProgress.floatValue },
                modifier = Modifier.fillMaxWidth(),
            )
          } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Status / Error Log
          Text(
              text = cloneStatus.value,
              style = MaterialTheme.typography.bodyMedium,
              color =
                  if (isCloneError.value) MaterialTheme.colorScheme.error
                  else MaterialTheme.colorScheme.onSurface,
          )

          Spacer(modifier = Modifier.height(24.dp))

          // Cancel Button / Close Button (Only active if error or user wants to abort - aborting
          // git clone hard without native support, usually just close dialog)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                  showCloneProgressDialog.value = false
                  isCloning.value = false
                },
                // Only enable cancel/close if error occurred or strictly needed.
                // Standard git clone shouldn't be interrupted easily without cleanup.
                // Here we allow it if user wants to give up waiting or see error.
                enabled = true,
            ) {
              Text(
                  if (isCloneError.value) stringResource(R.string.close)
                  else stringResource(R.string.cancel)
              )
            }
          }
        }
      }
    }
  }

  // 2. Open Project Confirmation Dialog
  if (showOpenProjectDialog.value && clonedRepoEntity.value != null) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
          showOpenProjectDialog.value = false
          onDismiss() // Close bottom sheet
        },
        title = { Text(text = stringResource(R.string.success)) },
        text = {
          Text(
              text =
                  stringResource(
                      R.string.msg_confirm_open_project,
                      clonedRepoEntity.value!!.repoName,
                  )
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                showOpenProjectDialog.value = false
                openProject(activityContext, clonedRepoEntity.value!!.fullSavePath)
                onDismiss() // Close bottom sheet
              }
          ) {
            Text(stringResource(R.string.yes))
          }
        },
        dismissButton = {
          TextButton(
              onClick = {
                showOpenProjectDialog.value = false
                onDismiss() // Close bottom sheet
              }
          ) {
            Text(stringResource(R.string.no))
          }
        },
    )
  }

  Surface(
      modifier = Modifier.fillMaxWidth().fillMaxHeight(),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      shape = MaterialTheme.shapes.extraLarge,
  ) {
    Column(modifier = Modifier.fillMaxHeight()) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onDismiss) {
          Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.back),
          )
        }

        Text(
            text = stringResource(R.string.clone),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )

        // 保存/确认按钮
        IconButton(
            onClick = {
              if (isCloning.value) return@IconButton
              if (!isReadyForClone.value) {
                Msg.requireShowLongDuration(
                    "Please check your input"
                )
                return@IconButton
              }
              cloneStatus.value = activityContext.getString(R.string.cloning)
              doSave()
            },
            // Keep clickable even when not ready so user gets explicit feedback.
            enabled = !isCloning.value,
        ) {
          if (isCloning.value) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
          } else {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(id = R.string.save),
                tint =
                    if (isReadyForClone.value) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
          }
        }
      }

      MyHorizontalDivider()

      // 内容区域
      Column(
          modifier =
              Modifier.weight(1f) // 占据剩余空间
                  .verticalScroll(listState)
                  .padding(bottom = MyStyleKt.Padding.PageBottom)
      ) {
        if (showLoadingDialog.value) {
          Box(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(text = loadingText.value)
          }
        }

        // New "Loading..." text above Git URL input
        if (isCloning.value) {
          Text(
              text = stringResource(R.string.loading) + "...",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              modifier =
                  Modifier.padding(horizontal = MyStyleKt.defaultItemPadding).padding(top = 8.dp),
          )
        }

        TextField(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(MyStyleKt.defaultItemPadding)
                    .focusRequester(focusRequesterGitUrl),
            singleLine = true,
            value = gitUrl.value,
            onValueChange = {
              gitUrl.value = it
              val repoNameFromGitUrl = getRepoNameFromGitUrl(it)
              if (repoNameFromGitUrl.isNotBlank() && repoName.value.text.isBlank()) {
                updateRepoName(TextFieldValue(repoNameFromGitUrl))
              }
              val newGitUrlType = Libgit2Helper.getGitUrlType(it)
              val newCredentialType = Libgit2Helper.getCredentialTypeByGitUrlType(newGitUrlType)
              curCredentialType.intValue = newCredentialType
              gitUrlType.intValue = newGitUrlType
            },
            label = {
              Row {
                Text(stringResource(R.string.git_url))
                Text(text = " (" + stringResource(id = R.string.http_https_ssh) + ")")
              }
            },
            placeholder = { Text(stringResource(R.string.git_url_placeholder)) },
            enabled = !isCloning.value, // Disable input while cloning
        )
        Spacer(modifier = Modifier.padding(spacerPadding))

        TextField(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(MyStyleKt.defaultItemPadding)
                    .focusRequester(focusRequesterRepoName),
            value = repoName.value,
            singleLine = true,
            isError =
                showRepoNameAlreadyExistsErr.value || showRepoNameHasIllegalCharsOrTooLongErr.value,
            supportingText = {
              val errMsg =
                  if (showRepoNameAlreadyExistsErr.value)
                      stringResource(R.string.repo_name_exists_err)
                  else if (showRepoNameHasIllegalCharsOrTooLongErr.value)
                      stringResource(R.string.err_repo_name_has_illegal_chars_or_too_long)
                  else ""

              if (
                  showRepoNameAlreadyExistsErr.value ||
                      showRepoNameHasIllegalCharsOrTooLongErr.value
              ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = errMsg,
                    color = MaterialTheme.colorScheme.error,
                )
              }
            },
            trailingIcon = {
              val errMsg =
                  if (showRepoNameAlreadyExistsErr.value)
                      stringResource(R.string.repo_name_exists_err)
                  else if (showRepoNameHasIllegalCharsOrTooLongErr.value)
                      stringResource(R.string.err_repo_name_has_illegal_chars_or_too_long)
                  else ""
              if (
                  showRepoNameAlreadyExistsErr.value ||
                      showRepoNameHasIllegalCharsOrTooLongErr.value
              ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = errMsg,
                    tint = MaterialTheme.colorScheme.error,
                )
              }
            },
            onValueChange = { updateRepoName(it) },
            label = { Text(stringResource(R.string.repo_name)) },
            enabled = !isCloning.value,
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
          val addIconSize = MyStyleKt.defaultIconSizeSmaller

          SingleSelectList(
              outterModifier = Modifier.align(Alignment.CenterStart),
              basePadding = { defaultHorizontalPadding ->
                PaddingValues(
                    end = addIconSize + 5.dp + defaultHorizontalPadding,
                    start = defaultHorizontalPadding,
                )
              },
              optionsList = storagePathList.value,
              selectedOptionIndex = storagePathSelectedIndex,
              selectedOptionValue = storagePathSelectedPath.value,
              menuItemFormatter = { _, value -> value?.name ?: "" },
              menuItemFormatterLine2 = { _, value -> value?.path ?: "" },
              menuItemOnClick = { index, value ->
                storagePathSelectedIndex.intValue = index
                storagePathSelectedPath.value = value
                StoragePathsMan.update { it.storagePathLastSelected = value.path }
              },
              menuItemTrailIcon = Icons.Filled.DeleteOutline,
              menuItemTrailIconDescription =
                  stringResource(R.string.trash_bin_icon_for_delete_item),
              menuItemTrailIconEnable = { index, value -> index != 0 },
              menuItemTrailIconOnClick = { index, value ->
                if (index == 0) {
                  Msg.requireShowLongDuration(
                      activityContext.getString(R.string.cant_delete_internal_storage)
                  )
                } else {
                  initDeleteStoragePathListDialog(index)
                }
              },
          )

          IconButton(
              modifier = Modifier.align(Alignment.CenterEnd),
              onClick = { showAddStoragePathDialog.value = true },
              enabled = !isCloning.value,
          ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_storage_path),
                modifier = Modifier.size(addIconSize),
            )
          }
        }

        Spacer(modifier = Modifier.padding(spacerPadding))

        TextField(
            modifier = Modifier.fillMaxWidth().padding(MyStyleKt.defaultItemPadding),
            value = branch.value,
            singleLine = true,
            onValueChange = { branch.value = it },
            label = { Text(stringResource(R.string.branch_optional)) },
            placeholder = { Text(stringResource(R.string.branch_name)) },
            enabled = !isCloning.value,
        )

        if (dev_EnableUnTestedFeature || shallowAndSingleBranchTestPassed) {
          val isPro = UserUtil.isPro()
          val enableSingleBranch = isPro && branch.value.isNotBlank()
          Row(
              Modifier.fillMaxWidth()
                  .height(MyStyleKt.CheckoutBox.height)
                  .toggleable(
                      enabled = enableSingleBranch && !isCloning.value,
                      value = isSingleBranch,
                      onValueChange = { onIsSingleBranchStateChange(!isSingleBranch) },
                      role = Role.Checkbox,
                  )
                  .padding(horizontal = MyStyleKt.defaultHorizontalPadding),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
                enabled = enableSingleBranch && !isCloning.value,
                checked = isSingleBranch,
                onCheckedChange = null,
            )
            Text(
                text =
                    if (isPro) stringResource(R.string.single_branch)
                    else stringResource(R.string.single_branch_pro_only),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp),
                color =
                    if (enableSingleBranch) Color.Unspecified
                    else if (inDarkTheme) MyStyleKt.TextColor.disable_DarkTheme
                    else MyStyleKt.TextColor.disable,
            )
          }

          Spacer(modifier = Modifier.padding(spacerPadding))
          DepthTextField(depth)
          Spacer(modifier = Modifier.padding(spacerPadding))
        }

        MyHorizontalDivider(modifier = Modifier.padding(spacerPadding))
        Spacer(Modifier.height(10.dp))

        val credentialListIsEmpty = allCredentialList.value.isEmpty()

        SingleSelection(
            itemList = credentialRadioOptions,
            selected = { idx, item -> credentialSelectedOption == idx },
            text = { idx, item -> item },
            onClick = { idx, item -> onCredentialOptionSelected(idx) },
            beforeShowItem = { idx, item ->
              if (idx == optNumNewCredential) {
                curCredentialType.intValue = Libgit2Helper.getCredentialTypeByUrl(gitUrl.value)
              }
            },
            skip = { idx, item -> credentialListIsEmpty && idx == optNumSelectCredential },
        )

        if (credentialSelectedOption == optNumNewCredential) {
          TextField(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(MyStyleKt.defaultItemPadding)
                      .focusRequester(focusRequesterCredentialName),
              isError = showCredentialNameAlreadyExistsErr.value,
              supportingText = {
                if (showCredentialNameAlreadyExistsErr.value) {
                  Text(
                      modifier = Modifier.fillMaxWidth(),
                      text = stringResource(R.string.credential_name_exists_err),
                      color = MaterialTheme.colorScheme.error,
                  )
                }
              },
              trailingIcon = {
                if (showCredentialNameAlreadyExistsErr.value)
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = stringResource(R.string.credential_name_exists_err),
                        tint = MaterialTheme.colorScheme.error,
                    )
              },
              singleLine = true,
              value = credentialName.value,
              onValueChange = { updateCredentialName(it) },
              label = { Text(stringResource(R.string.credential_name)) },
              placeholder = { Text(stringResource(R.string.credential_name_placeholder)) },
              enabled = !isCloning.value,
          )
          TextField(
              modifier =
                  if (curCredentialType.intValue == Cons.dbCredentialTypeSsh) {
                    Modifier.fillMaxWidth()
                        .heightIn(min = 300.dp, max = 300.dp)
                        .padding(MyStyleKt.defaultItemPadding)
                  } else {
                    Modifier.fillMaxWidth().padding(MyStyleKt.defaultItemPadding)
                  },
              singleLine = curCredentialType.intValue != Cons.dbCredentialTypeSsh,
              value = credentialVal.value,
              onValueChange = { credentialVal.value = it },
              label = {
                if (curCredentialType.intValue == Cons.dbCredentialTypeSsh) {
                  Text(stringResource(R.string.private_key))
                } else {
                  Text(stringResource(R.string.username))
                }
              },
              placeholder = {
                if (curCredentialType.intValue == Cons.dbCredentialTypeSsh) {
                  Text(stringResource(R.string.paste_your_private_key_here))
                } else {
                  Text(stringResource(R.string.username))
                }
              },
              enabled = !isCloning.value,
          )
          PasswordTextFiled(
              password = credentialPass,
              label =
                  if (curCredentialType.intValue == Cons.dbCredentialTypeSsh) {
                    stringResource(R.string.passphrase_if_have)
                  } else {
                    stringResource(R.string.password)
                  },
              placeholder =
                  if (curCredentialType.intValue == Cons.dbCredentialTypeSsh) {
                    stringResource(R.string.input_passphrase_if_have)
                  } else {
                    stringResource(R.string.password)
                  },
              passwordVisible = passwordVisible,
              enabled = !isCloning.value,
          )

          if (curCredentialType.intValue == Cons.dbCredentialTypeHttp) {
            TokenInsteadOfPasswordHint()
          }
        } else if (credentialSelectedOption == optNumSelectCredential) {
          Spacer(Modifier.height(MyStyleKt.defaultItemPadding))

          SingleSelectList(
              optionsList = allCredentialList.value,
              selectedOptionIndex = null,
              selectedOptionValue = selectedCredential.value,
              menuItemSelected = { _, item -> item.id == selectedCredential.value.id },
              menuItemFormatter = { _, item -> item?.name ?: "" },
              menuItemOnClick = { _, item -> selectedCredential.value = item },
          )
        } else if (credentialSelectedOption == optNumMatchCredentialByDomain) {
          MySelectionContainer {
            DefaultPaddingRow {
              Text(
                  stringResource(R.string.credential_match_by_domain_note),
                  color = MyStyleKt.TextColor.getHighlighting(),
                  fontWeight = FontWeight.Light,
              )
            }
          }
        }
      }
    }
  }

  if (requireFocusTo.intValue == focusToGitUrl) {
    requireFocusTo.intValue = focusToNone
    focusRequesterGitUrl.requestFocus()
  } else if (requireFocusTo.intValue == focusToRepoName) {
    requireFocusTo.intValue = focusToNone
    focusRequesterRepoName.requestFocus()
  } else if (requireFocusTo.intValue == focusToCredentialName) {
    requireFocusTo.intValue = focusToNone
    focusRequesterCredentialName.requestFocus()
  }

  LaunchedEffect(Unit) {
    doJobThenOffLoading job@{
      try {
        val paths = StoragePathsMan.get().storagePaths
        if (paths.isNotEmpty()) {
          val additionalPaths = paths.map {
            NameAndPath.genByPath(it, NameAndPathType.REPOS_STORAGE_PATH, activityContext)
          }
          val currentPaths = storagePathList.value.map { it.path }.toSet()
          val newPaths = additionalPaths.filter { it.path !in currentPaths }

          if (newPaths.isNotEmpty()) {
            storagePathList.value.addAll(newPaths)

            // 尝试恢复上次选择的路径
            val lastSelected = StoragePathsMan.get().storagePathLastSelected
            val found = storagePathList.value.find { it.path == lastSelected }
            if (found != null) {
              storagePathSelectedPath.value = found
              storagePathSelectedIndex.intValue = storagePathList.value.indexOf(found)
            }
          }
        }
      } catch (e: Exception) {
        MyLog.e(TAG, "Failed to load storage paths in LaunchedEffect: ${e.message}")
      }

      if (isEditMode) {
        val repoDb = AppModel.dbContainer.repoRepository
        val credentialDb = AppModel.dbContainer.credentialRepository
        val repo = repoDb.getById(repoId)
        if (repo == null) {
          Msg.requireShowLongDuration(activityContext.getString(R.string.repo_id_invalid))
          return@job
        }
        gitUrlType.intValue = Libgit2Helper.getGitUrlType(repo.cloneUrl)
        gitUrl.value = repo.cloneUrl
        repoName.value = TextFieldValue(repo.repoName)
        branch.value = repo.branch
        onIsSingleBranchStateChange(dbIntToBool(repo.isSingleBranch))
        onIsRecursiveCloneStateChange(dbIntToBool(repo.isRecursiveCloneOn))
        if (Libgit2Helper.needSetDepth(repo.depth)) {
          depth.value = "" + repo.depth
        }

        repoFromDb.value = repo

        val storagePath = File(repo.fullSavePath).parent ?: ""
        val (selectedStoragePathIdx, selectedStoragePathItem) =
            findStoragePathItemByPath(storagePath)
        storagePathSelectedIndex.intValue = selectedStoragePathIdx
        storagePathSelectedPath.value =
            selectedStoragePathItem
                ?: NameAndPath.genByPath(
                    storagePath,
                    NameAndPathType.REPOS_STORAGE_PATH,
                    activityContext,
                )

        val credentialIdForClone = repo.credentialIdForClone
        if (!credentialIdForClone.isNullOrBlank()) {
          if (credentialIdForClone == SpecialCredential.MatchByDomain.credentialId) {
            onCredentialOptionSelected(optNumMatchCredentialByDomain)
          } else {
            val credential = credentialDb.getById(credentialIdForClone)
            if (credential == null) {
              onCredentialOptionSelected(optNumNoCredential)
            } else {
              onCredentialOptionSelected(optNumSelectCredential)
              selectedCredential.value = credential
              curCredentialType.intValue = Libgit2Helper.getCredentialTypeByUrl(repo.cloneUrl)
            }
          }
        }
      } else {
        requireFocusTo.intValue = focusToGitUrl
      }

      val credentialDb = AppModel.dbContainer.credentialRepository
      allCredentialList.value.clear()
      allCredentialList.value.addAll(credentialDb.getAll())
    }
  }

  isReadyForClone.value =
      ((gitUrl.value.isNotBlank() && repoName.value.text.isNotBlank()) &&
          ((credentialSelectedOption == optNumNoCredential ||
              credentialSelectedOption == optNumMatchCredentialByDomain) ||
              ((credentialSelectedOption == optNumNewCredential &&
                  credentialName.value.text.isNotBlank())) ||
              (credentialSelectedOption == optNumSelectCredential &&
                  selectedCredential.value.id.isNotBlank() &&
                  selectedCredential.value.name.isNotBlank())) &&
          !showRepoNameAlreadyExistsErr.value &&
          !showRepoNameHasIllegalCharsOrTooLongErr.value &&
          !showCredentialNameAlreadyExistsErr.value)
}

@Composable
fun DefaultPaddingRow(content: @Composable RowScope.() -> Unit) {
  Row(modifier = Modifier.padding(MyStyleKt.defaultItemPadding), content = content)
}
