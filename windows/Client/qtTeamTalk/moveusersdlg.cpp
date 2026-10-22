/*
 * Copyright (C) 2023, Bjørn D. Rasmussen, BearWare.dk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "moveusersdlg.h"
#include "utilui.h"
#include "appinfo.h"
#include <QKeyEvent>
#include <QMessageBox>
#include <algorithm>

extern TTInstance* ttInst;

MoveUsersDlg::MoveUsersDlg(const QVector<int>& userIds,
                           const QVector<int>& channelIds,
                           const QVector<int>& preselectedUserIds,
                           int preselectedChannelId,
                           QWidget *parent)
    : QDialog(parent), userIds(userIds), channelIds(channelIds)
{
    setupUi();
    populateUsers(preselectedUserIds);
    populateChannels(preselectedChannelId);
}

void MoveUsersDlg::setupUi()
{
    setWindowTitle(tr("Move Users"));
    setWindowIcon(QIcon(APPICON));
    setMinimumWidth(380);
    setMinimumHeight(450);

    QVBoxLayout* mainLayout = new QVBoxLayout(this);

    QLabel* usersLabel = new QLabel(tr("Users"), this);
    usersListWidget = new QListWidget(this);
    usersLabel->setBuddy(usersListWidget);
    usersListWidget->setSelectionMode(QAbstractItemView::SingleSelection);
    usersListWidget->installEventFilter(this);

    mainLayout->addWidget(usersLabel);
    mainLayout->addWidget(usersListWidget, 1);

    QLabel* channelLabel = new QLabel(tr("&Channel"), this);
    channelComboBox = new QComboBox(this);
    channelLabel->setBuddy(channelComboBox);
    channelComboBox->installEventFilter(this);

    mainLayout->addWidget(channelLabel);
    mainLayout->addWidget(channelComboBox);

    QHBoxLayout* buttonsLayout = new QHBoxLayout;
    moveBtn = new QPushButton(tr("Move Users"), this);
    cancelBtn = new QPushButton(tr("Cancel"), this);
    moveBtn->setDefault(true);
    buttonsLayout->addStretch();
    buttonsLayout->addWidget(moveBtn);
    buttonsLayout->addWidget(cancelBtn);
    mainLayout->addLayout(buttonsLayout);

    connect(moveBtn, &QPushButton::clicked, this, &MoveUsersDlg::slotMoveClicked);
    connect(cancelBtn, &QPushButton::clicked, this, &MoveUsersDlg::reject);

    setTabOrder(usersListWidget, channelComboBox);
    setTabOrder(channelComboBox, moveBtn);
    setTabOrder(moveBtn, cancelBtn);
}

void MoveUsersDlg::populateUsers(const QVector<int>& preselectedUserIds)
{
    usersListWidget->clear();

    struct UserEntry {
        int id;
        QString name;
    };
    QVector<UserEntry> entries;

    for (int uid : userIds)
    {
        User u;
        if (TT_GetUser(ttInst, uid, &u))
            entries.append({uid, getDisplayName(u)});
    }

    std::sort(entries.begin(), entries.end(), [](const UserEntry& a, const UserEntry& b) {
        return a.name.localeAwareCompare(b.name) < 0;
    });

    QListWidgetItem* firstCheckedItem = nullptr;

    for (const auto& entry : entries)
    {
        QListWidgetItem* item = new QListWidgetItem(entry.name, usersListWidget);
        item->setFlags(Qt::ItemIsEnabled | Qt::ItemIsSelectable | Qt::ItemIsUserCheckable);
        bool checked = preselectedUserIds.contains(entry.id);
        item->setCheckState(checked ? Qt::Checked : Qt::Unchecked);
        item->setData(Qt::UserRole, entry.id);

        if (checked && !firstCheckedItem)
            firstCheckedItem = item;
    }

    if (firstCheckedItem)
        usersListWidget->setCurrentItem(firstCheckedItem);
    else if (usersListWidget->count() > 0)
        usersListWidget->setCurrentRow(0);

    usersListWidget->setFocus();
}

void MoveUsersDlg::populateChannels(int preselectedChannelId)
{
    channelComboBox->clear();
    int rootId = TT_GetRootChannelID(ttInst);
    int selectIdx = 0;

    for (int channelId : channelIds)
    {
        Channel c;
        if (TT_GetChannel(ttInst, channelId, &c))
        {
            QString chanName;
            if (channelId == rootId)
            {
                chanName = tr("Root Channel");
            }
            else
            {
                TTCHAR szPath[TT_STRLEN];
                if (TT_GetChannelPath(ttInst, channelId, szPath) && c.nParentID != rootId)
                    chanName = _Q(szPath);
                else
                    chanName = _Q(c.szName);
            }
            channelComboBox->addItem(chanName, channelId);
            if (preselectedChannelId > 0 && channelId == preselectedChannelId)
                selectIdx = channelComboBox->count() - 1;
        }
    }

    if (channelComboBox->count() > 0)
        channelComboBox->setCurrentIndex(selectIdx);
}

bool MoveUsersDlg::eventFilter(QObject *watched, QEvent *event)
{
    if (watched == usersListWidget && event->type() == QEvent::KeyPress)
    {
        QKeyEvent *keyEvent = static_cast<QKeyEvent *>(event);
        int key = keyEvent->key();
        if (key == Qt::Key_Up)
        {
            if (usersListWidget->currentRow() <= 0)
                return true;
        }
        else if (key == Qt::Key_Down)
        {
            if (usersListWidget->currentRow() >= usersListWidget->count() - 1)
                return true;
        }
        else if (key == Qt::Key_Left || key == Qt::Key_Right)
        {
            return true;
        }
        else if (key == Qt::Key_Space || key == Qt::Key_Return || key == Qt::Key_Enter)
        {
            QListWidgetItem* item = usersListWidget->currentItem();
            if (item && (item->flags() & Qt::ItemIsUserCheckable))
            {
                item->setCheckState(item->checkState() == Qt::Checked ? Qt::Unchecked : Qt::Checked);
                return true;
            }
        }
    }
    else if (watched == channelComboBox && event->type() == QEvent::KeyPress)
    {
        QKeyEvent *keyEvent = static_cast<QKeyEvent *>(event);
        int key = keyEvent->key();
        if (key == Qt::Key_Up)
        {
            if (channelComboBox->currentIndex() <= 0)
                return true;
        }
        else if (key == Qt::Key_Down)
        {
            if (channelComboBox->currentIndex() >= channelComboBox->count() - 1)
                return true;
        }
        else if (key == Qt::Key_Left || key == Qt::Key_Right)
        {
            return true;
        }
    }
    return QDialog::eventFilter(watched, event);
}

void MoveUsersDlg::slotMoveClicked()
{
    if (getSelectedUserIds().isEmpty())
    {
        QMessageBox::information(this, tr("Move Users"), tr("No users selected to move."));
        return;
    }
    accept();
}

QVector<int> MoveUsersDlg::getSelectedUserIds() const
{
    QVector<int> selectedUserIds;
    for (int i = 0; i < usersListWidget->count(); ++i)
    {
        QListWidgetItem* item = usersListWidget->item(i);
        if (item && item->checkState() == Qt::Checked)
            selectedUserIds.append(item->data(Qt::UserRole).toInt());
    }
    return selectedUserIds;
}

int MoveUsersDlg::getSelectedChannelId() const
{
    return channelComboBox->currentData().toInt();
}
