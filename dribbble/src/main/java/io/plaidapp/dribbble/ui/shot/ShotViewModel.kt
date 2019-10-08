/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.plaidapp.dribbble.ui.shot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import io.plaidapp.core.data.CoroutinesDispatcherProvider
import io.plaidapp.core.data.Result
import io.plaidapp.core.dribbble.data.ShotsRepository
import io.plaidapp.core.util.event.Event
import io.plaidapp.dribbble.domain.CreateShotUiModelUseCase
import io.plaidapp.dribbble.domain.GetShareShotInfoUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject

/**
 * View model for [ShotActivity].
 */
class ShotViewModel @Inject constructor(
    shotId: Long,
    shotsRepository: ShotsRepository,
    private val createShotUiModel: CreateShotUiModelUseCase,
    private val getShareShotInfo: GetShareShotInfoUseCase,
    private val dispatcherProvider: CoroutinesDispatcherProvider
) : ViewModel() {
    //private val _shotUiModel = MutableLiveData<ShotUiModel>()
    val shotUiModel = liveData(
        context = viewModelScope.coroutineContext
    ) {
        val result = shotsRepository.getShot(shotId)
        if (result is Result.Success) {
            emit(result.data.toShotUiModel())
            // this is for testing so that it can see two values :/.
            yield()
            emit(createShotUiModel(result.data))
        } else {
            // TODO re-throw Error.exception once Loading state removed.
            throw IllegalStateException("Could not retrieve shot $shotId")
        }
    }

    private val openLinkRequest = Channel<Unit>(Channel.CONFLATED)
    val openLink = liveData {
        openLinkRequest.consume {
            val model = shotUiModel.asFlow().first()
            emit(Event(model.url))
        }
    }

    private val shareShotRequest = Channel<Unit>(Channel.CONFLATED)

    val shareShot = liveData {
        shareShotRequest.consume {
            withContext(dispatcherProvider.io) {
                val model = shotUiModel.asFlow().first()
                val shareInfo = getShareShotInfo(model)
                emit(Event(shareInfo))
            }
        }
    }

    fun shareShotRequested() {
        shareShotRequest.offer(Unit)
    }

    fun viewShotRequested() {
        openLinkRequest.offer(Unit)
    }

    fun getAssistWebUrl(): String {
        return shotUiModel.value?.url.orEmpty()
    }

    fun getShotId(): Long {
        return shotUiModel.value?.id ?: -1L
    }
}
