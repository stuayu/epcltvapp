package com.daigorian.epcltvapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.*
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.daigorian.epcltvapp.epgstationcaller.EpgStation
import com.daigorian.epcltvapp.epgstationcaller.GetRecordedParam
import com.daigorian.epcltvapp.epgstationcaller.GetRecordedResponse
import com.daigorian.epcltvapp.epgstationcaller.RecordedProgram
import com.daigorian.epcltvapp.epgstationv2caller.EpgStationV2
import com.daigorian.epcltvapp.epgstationv2caller.GetRecordedParamV2
import com.daigorian.epcltvapp.epgstationv2caller.RecordedItem
import com.daigorian.epcltvapp.epgstationv2caller.Records
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.roundToInt

/**
 * A wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its metadata plus related videos.
 */
class VideoDetailsFragment : DetailsSupportFragment() {

    private var mSelectedRecordedProgram: RecordedProgram? = null
    private var mSelectedRecordedItem: RecordedItem? = null

    private lateinit var mDetailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var mPresenterSelector: ClassPresenterSelector
    private lateinit var mAdapter: DeleteEnabledArrayObjectAdapter
    private val mCardPresenter = OriginalCardPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate DetailsFragment")
        super.onCreate(savedInstanceState)

        mDetailsBackground = DetailsSupportFragmentBackgroundController(this)

        // EPGStationのバージョンによってintentで渡されてくるオブジェクトタイプが違う。
        // EPGStation Version 1.x.x
        mSelectedRecordedProgram = requireActivity().intent.getSerializableExtra(DetailsActivity.RECORDEDPROGRAM) as RecordedProgram?
        // EPGStation Version 2.x.x
        mSelectedRecordedItem= requireActivity().intent.getSerializableExtra(DetailsActivity.RECORDEDITEM) as RecordedItem?

        when {
            mSelectedRecordedProgram != null -> {
                // EPGStation Version 1.x.x
                mPresenterSelector = ClassPresenterSelector()
                mAdapter = DeleteEnabledArrayObjectAdapter(mPresenterSelector)
                mCardPresenter.objAdapter = mAdapter
                setupDetailsOverviewRow()
                setupDetailsOverviewRowPresenter()
                updateRelatedMovieListRow()
                adapter = mAdapter
                initializeBackground(EpgStation.getThumbnailURL(mSelectedRecordedProgram?.id.toString()))
                onItemViewClickedListener = ItemViewClickedListener()
                setOnItemViewSelectedListener( ItemViewSelectedListener())
            }
            mSelectedRecordedItem != null -> {
                // EPGStation Version 2.x.x
                mPresenterSelector = ClassPresenterSelector()
                mAdapter = DeleteEnabledArrayObjectAdapter(mPresenterSelector)
                mCardPresenter.objAdapter = mAdapter
                setupDetailsOverviewRow()
                setupDetailsOverviewRowPresenter()
                updateRelatedMovieListRow()
                adapter = mAdapter
                initializeBackground(
                    EpgStationV2.getThumbnailURL(
                        if(mSelectedRecordedItem?.thumbnails?.isNotEmpty() == true)
                            {
                                mSelectedRecordedItem?.thumbnails?.get(0).toString()
                            }else{
                                ""
                            })
                )
                onItemViewClickedListener = ItemViewClickedListener()
                setOnItemViewSelectedListener( ItemViewSelectedListener())
            }
            else -> {
                val intent = Intent(requireContext(), MainActivity::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateRelatedMovieListRow()
    }

    private fun initializeBackground(imageURL: String) {
        mDetailsBackground.enableParallax()

        //Glideでイメージを取得する際にBasic認証が必要な場合はヘッダを付与してやる
        val glideUrl = if(EpgStation.api!=null && EpgStation.authForGlide!=null){
            GlideUrl( imageURL, EpgStation.authForGlide)
        }else if(EpgStationV2.api!=null && EpgStationV2.authForGlide!=null){
            GlideUrl( imageURL, EpgStationV2.authForGlide)
        }else{
            GlideUrl ( imageURL )
        }

        Glide.with(requireContext())
            .asBitmap()
            .centerCrop()
            .error(R.drawable.default_background)
            .load(glideUrl)
            .into<CustomTarget<Bitmap>>(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    bitmap: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    mDetailsBackground.coverBitmap = bitmap
                    mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size())
                }

                override fun onLoadCleared( placeholder: Drawable?) {}
            })
    }

    private fun setupDetailsOverviewRow() {
        Log.d(TAG, "setupDetailsOverviewRow()")

        val row = if (mSelectedRecordedProgram!=null){
            // EPGStation Version 1.x.x
            DetailsOverviewRow(mSelectedRecordedProgram)
        }else {
            // EPGStation Version 2.x.x
            DetailsOverviewRow(mSelectedRecordedItem)
        }
        val urlString = if (mSelectedRecordedProgram!=null){
            // EPGStation Version 1.x.x
            EpgStation.getThumbnailURL(mSelectedRecordedProgram?.id.toString())
        }else {
            // EPGStation Version 2.x.x
            EpgStationV2.getThumbnailURL(
                if(mSelectedRecordedItem?.thumbnails?.isNotEmpty() == true)
                {
                    mSelectedRecordedItem?.thumbnails?.get(0).toString()
                }else{
                    ""
                })
        }

        row.imageDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.default_background)
        val width = convertDpToPixel(requireContext(), DETAIL_THUMB_WIDTH)
        val height = convertDpToPixel(requireContext(), DETAIL_THUMB_HEIGHT)

        //Glideでイメージを取得する際にBasic認証が必要な場合はヘッダを付与してやる
        val glideUrl = if(EpgStation.api!=null && EpgStation.authForGlide!=null){
            GlideUrl( urlString, EpgStation.authForGlide)
        }else if(EpgStationV2.api!=null && EpgStationV2.authForGlide!=null){
            GlideUrl( urlString, EpgStationV2.authForGlide)
        }else{
            GlideUrl ( urlString )
        }

        Glide.with(requireContext())
            .load(glideUrl)
            .centerCrop()
            .error(R.drawable.default_background)
            .into<CustomTarget<Drawable>>(object : CustomTarget<Drawable>(width, height) {
                override fun onResourceReady(
                    drawable: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    Log.d(TAG, "details overview card image url ready: $drawable")
                    row.imageDrawable = drawable
                    mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size())
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    //サムネのロードが失敗したので、録画中かどうか調べてだし分けする。
                    super.onLoadFailed(errorDrawable)
                    Log.d(TAG, "details overview card image url read on fail: $errorDrawable")
                    mSelectedRecordedProgram?.let {
                        // EPGStation Version 1.x.x
                        if (it.recording) {
                            row.imageDrawable =
                                ContextCompat.getDrawable(context!!, R.drawable.on_rec)
                        } else{
                            row.imageDrawable=
                                ContextCompat.getDrawable(context!!, R.drawable.no_iamge)
                        }
                    }
                    mSelectedRecordedItem?.let{
                        // EPGStation Version 2.x.x
                        if (it.isRecording){
                            row.imageDrawable =
                                ContextCompat.getDrawable(context!!, R.drawable.on_rec)
                        } else{
                            row.imageDrawable=
                                ContextCompat.getDrawable(context!!, R.drawable.no_iamge)
                        }
                    }
                    mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size())
                }
                override fun onLoadCleared( placeholder: Drawable?) {}
            })

        val actionAdapter = ArrayObjectAdapter()

        mSelectedRecordedProgram?.let {
            // EPGStation Version 1.x.x
            // オリジナルのTSがある場合は "TS を再生" アクションアダプタを追加
            if (it.original) {
                actionAdapter.add(
                    Action(
                        ACTION_WATCH_ORIGINAL_TS,
                        getString(R.string.play_ts)
                    )
                )
            }
            // エンコード済みがある場合は "XX を再生" アクションアダプタを追加
            it.encoded?.forEach { encodedProgram ->
                actionAdapter.add(
                    Action(
                        encodedProgram.encodedId,
                        getString(R.string.play_x,encodedProgram.name)
                    )
                )
            }
        }
        mSelectedRecordedItem?.let {  recordedItem ->
            // EPGStation Version 2.x.x
            recordedItem.videoFiles?.forEach { videoFIle ->
                actionAdapter.add(
                    Action(
                        videoFIle.id,
                        getString(R.string.play_x,videoFIle.name)
                    )
                )
            }
        }



        row.actionsAdapter = actionAdapter

        mAdapter.add(row)
    }

    private fun setupDetailsOverviewRowPresenter() {
        Log.d(TAG, "setupDetailsOverviewRowPresenter()")

        // Set detail background.
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        detailsPresenter.backgroundColor =
            ContextCompat.getColor(requireContext(), R.color.selected_background)
        // Hook up transition element.
        val sharedElementHelper = FullWidthDetailsOverviewSharedElementHelper()
        sharedElementHelper.setSharedElementEnterTransition(
            activity, DetailsActivity.SHARED_ELEMENT_NAME
        )
        detailsPresenter.setListener(sharedElementHelper)
        detailsPresenter.isParticipatingEntranceTransition = true

        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->

            val playerPkgName = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(getString(R.string.pref_key_player),"")
            if( playerPkgName == getString(R.string.pref_options_movie_player_val_INTERNAL)) {
                //Preferenceで内蔵プレーヤーが選ばれていた場合
                val intent = Intent(requireContext(), PlaybackActivity::class.java)
                //EPGStation Version 1.x.x
                mSelectedRecordedProgram?.let{intent.putExtra(DetailsActivity.RECORDEDPROGRAM, mSelectedRecordedProgram)}
                //EPGStation Version 2.x.x
                mSelectedRecordedItem?.let{intent.putExtra(DetailsActivity.RECORDEDITEM, mSelectedRecordedItem)}
                intent.putExtra(DetailsActivity.ACTIONID, action.id)
                startActivity(intent)

            }else {
                // Preferenceで外部プレーヤーが選ばれていた場合
                val urlStrings = if (mSelectedRecordedProgram != null) {
                    // EPGStation Version 1.x.x
                    if (action.id == ACTION_WATCH_ORIGINAL_TS) {
                        // TSだった場合
                        EpgStation.getTsVideoURL(mSelectedRecordedProgram?.id.toString())
                    } else {
                        // Encodedだった場合
                        EpgStation.getEncodedVideoURL(
                            mSelectedRecordedProgram?.id.toString(),
                            action.id.toString()  )
                    }
                }else{
                    // EPGStation Version 2.x.x
                    EpgStationV2.getVideoURL(action.id.toString())
                }

                val uri = Uri.parse(urlStrings)
                val extPlayerIntent = Intent(Intent.ACTION_VIEW)
                extPlayerIntent.setPackage(playerPkgName)
                extPlayerIntent.setDataAndTypeAndNormalize(uri, "video/*")
                // EPGStation Version 1.x.x
                mSelectedRecordedProgram?.let{extPlayerIntent.putExtra("title", mSelectedRecordedProgram?.name)}
                // EPGStation Version 2.x.x
                mSelectedRecordedItem?.let{extPlayerIntent.putExtra("title", mSelectedRecordedItem?.name)}

                try {
                    startActivity(extPlayerIntent)
                } catch (ex: ActivityNotFoundException) {
                    //外部プレーヤーがインストールされていなからインストールしてねのメッセージを表示
                    Toast.makeText(requireContext(), getString(R.string.please_install_external_player), Toast.LENGTH_LONG).show()
                    try{
                        //Google Play Storeで外部プレイヤーのページを表示
                        val marketIntent = Intent(Intent.ACTION_VIEW)
                        marketIntent.data = Uri.parse("market://details?id=$playerPkgName")
                        startActivity(marketIntent)
                    } catch (ex: ActivityNotFoundException) {
                        //Google Play Storeすら導入されていないからどうしようもできない。
                    }
                }
            }

        }
        mPresenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
    }


    private fun updateRelatedMovieListRow() {
        Log.d(TAG, "updateRelatedMovieListRow()")
        // 関連動画一覧の生成
        //  - 現在表示中の動画の名前にregexDelimiterがあれば、その前までの文字列をシリーズ名とみなして一覧を表示
        //  - 現在表示中の動画と同じルールIDを持った動画を検索して一覧を表示

        // 新しい正規表現を定義
        val regexTitleExtraction = """^(?:\[[^\]]*\]|\【[^】]*\】|\(吹\)|［[^］]*］)?\s*([^\d【【\s\[\]\d]+(?:\s*[^\d【【\s\[\]]+)*?)\s*(?:＃\d+|第[0-9０-９]+[話回]|【[^】]*\】|\[\d*\])?.*""".toRegex()

        // 元のタイトルに対する処理
        val originalTitle: String = if (mSelectedRecordedProgram != null) mSelectedRecordedProgram!!.name
                                    else mSelectedRecordedItem!!.name

        // 行頭に[新]などがあった場合は消しておく
        val programNameStriped = originalTitle.replace(regexDeleteStr, "")

        // 正規表現を使って正確なタイトルを抽出
        val matchResult = regexTitleExtraction.find(programNameStriped)
        val programName = matchResult?.groups?.get(1)?.value?.trim() ?: ""

        // 区切り文字が見つかった場合、シリーズとして表示する
        if (programName.isNotEmpty()) {
            val searchKeyword = programName
            mAdapter.updateContentsListRow(
                GetRecordedParam(keyword = searchKeyword),
                GetRecordedParamV2(keyword = searchKeyword),
                searchKeyword,
                0,
                mCardPresenter,
                requireContext()
            )
        }

        val ruleId =if(mSelectedRecordedProgram!=null) mSelectedRecordedProgram!!.ruleId
        else mSelectedRecordedItem!!.ruleId

        mAdapter.updateContentsListRow(
            GetRecordedParam(rule = ruleId),
            GetRecordedParamV2(ruleId = ruleId),
            getString(R.string.videos_in_same_rule),
            1,
            mCardPresenter,
            requireContext()
        )
        mPresenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())

    }

    private fun convertDpToPixel(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return (dp.toFloat() * density).roundToInt()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            if (item is RecordedProgram) {
                //EPGStation Version 1.x.x
                Log.d(TAG, "Item: $item")
                val intent = Intent(context!!, DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.RECORDEDPROGRAM, item)

                val bundle =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity!!,
                        (itemViewHolder?.view as ImageCardView).mainImageView,
                        DetailsActivity.SHARED_ELEMENT_NAME
                    )
                        .toBundle()
                startActivity(intent, bundle)
            } else if (item is RecordedItem) {
                //EPGStation Version 2.x.x
                Log.d(TAG, "Item: $item")
                val intent = Intent(context!!, DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.RECORDEDITEM, item)

                val bundle =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity!!,
                        (itemViewHolder?.view as ImageCardView).mainImageView,
                        DetailsActivity.SHARED_ELEMENT_NAME
                    )
                        .toBundle()
                startActivity(intent, bundle)
            }
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?, item: Any?,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            when (item) {
                is GetRecordedParam -> {
                    // EPGStation Version 1.x.x の続きを取得するアイテム
                    val adapter =  ((row as ListRow).adapter as ArrayObjectAdapter)

                    //APIで続きを取得して続きに加えていく
                    // EPGStation V1.x.x
                    EpgStation.api?.getRecorded(
                        limit = item.limit,
                        offset = item.offset,
                        reverse = item.reverse,
                        rule = item.rule,
                        genre1 = item.genre1,
                        channel = item.channel,
                        keyword = item.keyword,
                        hasTs = item.hasTs,
                        recording = item.recording
                    )?.enqueue(object : Callback<GetRecordedResponse> {
                        override fun onResponse(call: Call<GetRecordedResponse>, response: Response<GetRecordedResponse>) {
                            response.body()?.let { getRecordedResponse ->

                                //APIのレスポンスをひとつづつアイテムとして加える。最初のアイテムだけ、Loadingアイテムを置き換える
                                //先にremoveしてaddすると高速でスクロールさせたときに描画とremoveがぶつかって落ちるのであえてreplaceに。
                                getRecordedResponse.recorded.forEachIndexed {  index, recordedProgram ->
                                    if(index == 0) {
                                        adapter.replace(adapter.indexOf(item),recordedProgram)
                                    }else{
                                        adapter.add(recordedProgram)
                                    }
                                }
                                //続きがあるなら"次を読み込む"を置く。
                                val numOfItem = getRecordedResponse.recorded.count().toLong() + item.offset
                                if (numOfItem < getRecordedResponse.total) {
                                    adapter.add(item.copy(offset = numOfItem))
                                }

                            }
                        }
                        override fun onFailure(call: Call<GetRecordedResponse>, t: Throwable) {
                            Log.d(TAG,"onItemSelected() getRecorded API Failure")
                            Toast.makeText(context!!, getString(R.string.connect_epgstation_failed), Toast.LENGTH_LONG).show()
                        }
                    })
                }
                is GetRecordedParamV2 -> {
                    // EPGStation Version 1.x.x の続きを取得するアイテム
                    val adapter =  ((row as ListRow).adapter as ArrayObjectAdapter)

                    //APIで続きを取得して続きに加えていく
                    // EPGStation V2.x.x
                    EpgStationV2.api?.getRecorded(
                        isHalfWidth = item.isHalfWidth,
                        offset = item.offset,
                        limit = item.limit,
                        isReverse = item.isReverse,
                        ruleId = item.ruleId,
                        channelId = item.channelId,
                        genre = item.genre,
                        keyword = item.keyword,
                        hasOriginalFile = item.hasOriginalFile
                    )?.enqueue(object : Callback<Records> {
                        override fun onResponse(call: Call<Records>, response: Response<Records>) {
                            response.body()?.let { responseRoot ->

                                //APIのレスポンスをひとつづつアイテムとして加える。最初のアイテムだけ、Loadingアイテムを置き換える
                                //先にremoveしてaddすると高速でスクロールさせたときに描画とremoveがぶつかって落ちるのであえてreplaceに。
                                responseRoot.records.forEachIndexed {  index, recordedProgram ->
                                    if(index == 0) {
                                        adapter.replace(adapter.indexOf(item),recordedProgram)
                                    }else{
                                        adapter.add(recordedProgram)
                                    }
                                }
                                //続きがあるなら"次を読み込む"を置く。
                                val numOfItem = responseRoot.records.count().toLong() + item.offset
                                if (numOfItem < responseRoot.total) {
                                    adapter.add(item.copy(offset = numOfItem))
                                }

                            }
                        }
                        override fun onFailure(call: Call<Records>, t: Throwable) {
                            Log.d(TAG,"onItemSelected() getRecorded API Failure")
                            Toast.makeText(context!!, getString(R.string.connect_epgstation_failed), Toast.LENGTH_LONG).show()
                        }
                    })
                }

            }
        }
    }

    companion object {
        private const val TAG = "VideoDetailsFragment"

        internal const val ACTION_WATCH_ORIGINAL_TS = 0L

        private const val DETAIL_THUMB_WIDTH = 274
        private const val DETAIL_THUMB_HEIGHT = 274
    }
}
